package com.lab.inventory.service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.benmanes.caffeine.cache.Cache;
import com.lab.inventory.config.RabbitConfig;
import com.lab.inventory.dto.CreateProductRequestDTO;
import com.lab.inventory.model.Product;
import com.lab.inventory.repository.ProductRepository;



@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final RedissonClient redissonClient;
    private final Cache<Long, Product> localCache;
    private final RabbitTemplate rabbitTemplate;

    ProductService(ProductRepository productRepository, RedissonClient redissonClient, Cache<Long, Product> localCache, RabbitTemplate rabbitTemplate) {
        this.productRepository = productRepository;
        this.redissonClient = redissonClient;
        this.localCache = localCache;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public Long createProduct(CreateProductRequestDTO request) {

        Product product = Product.builder()
                .name(request.name())
                .price(request.price())
                .stock(request.stock())
                .build();

        return productRepository.save(product).getId();
    }

    public Optional<Product>  getProductById(Long id) {
        Product localProduct = localCache.getIfPresent(id);
        if (localProduct != null) {
            System.out.println("Caffeine Cache hit for product id: " + id);
            return Optional.of(localProduct);
        }
        RBucket<Product> redisBucket = redissonClient.getBucket("product:"+id);
        Product redisProduct = redisBucket.get();

        if(redisProduct != null) {
            System.out.println("Redis Cache hit for product id: " + id);
            localCache.put(id, redisProduct);
            return Optional.of(redisProduct);
        }

        System.out.println("Cache miss for product id: " + id);
        System.out.println("Fetching product id " + id + " from database.");

        Optional<Product> dbProductOpt  = productRepository.findById(id);
        if(dbProductOpt.isPresent()) {
            Product product = dbProductOpt.get();
            redisBucket.set(product, Duration.ofMinutes(10));

            localCache.put(id, product);

        }

        return dbProductOpt;
    }

    @Transactional
    public void buyProduct(Long id, Integer quantity) {
        String lockKey = "lock:product:" + id;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(5, 10, TimeUnit.SECONDS);
            if(acquired) {
                try {
                    Product product = productRepository.findById(id)
                            .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

                    if(product.getStock() >= quantity) {
                        product.setStock(product.getStock() - quantity);
                        productRepository.save(product);

                        redissonClient.getBucket("product:"+id).delete();

                        sendInvalidationEvent(id);

                        System.out.println("Purchase complete by: " + Thread.currentThread().getName());
                        System.out.println("Product id " + id + " purchased, quantity: " + quantity);
                    } else {
                        throw new RuntimeException("Insufficient stock for product id: " + id);
                    } 
                } finally {
                    lock.unlock();
                }
            } else {
                throw new RuntimeException("Could not complete purchase");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Error while trying to get lock", e);
        }
    }

    private void sendInvalidationEvent(Long productId) {
        rabbitTemplate.convertAndSend(RabbitConfig.PRODUCT_UPDATE_EXCHANGE, "", productId);
        System.out.println("Sent cache invalidation event for product id: " + productId);
    }

    @RabbitListener(queues = "#{myInstanceQueue.name}")
    public void handleInvalidationEvent(Long productId) {
        localCache.invalidate(productId);
        System.out.println("Invalidated local cache using event for product id: " + productId);
    }
}
