package com.lab.inventory.service;

import java.time.Duration;
import java.util.Optional;

import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.benmanes.caffeine.cache.Cache;
import com.lab.inventory.dto.CreateProductRequestDTO;
import com.lab.inventory.model.Product;
import com.lab.inventory.repository.ProductRepository;



@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final RedissonClient redissonClient;
    private final Cache<Long, Product> localCache;

    ProductService(ProductRepository productRepository, RedissonClient redissonClient,
            Cache<Long, Product> localCache) {
        this.productRepository = productRepository;
        this.redissonClient = redissonClient;
        this.localCache = localCache;
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
}
