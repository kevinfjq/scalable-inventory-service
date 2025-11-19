package com.lab.inventory.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.lab.inventory.model.Product;
import com.lab.inventory.repository.ProductRepository;
import com.lab.inventory.service.ProductService;

@SpringBootTest
public class ConcurrencyTest {
    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    public void testRaceCondition_WhenBuyingProduct_WithoutLock() throws InterruptedException {
        Product product = Product.builder()
                .name("PS5")
                .price(BigDecimal.valueOf(2800))
                .stock(1)
                .build();
        product = productRepository.save(product);
        Long productId = product.getId();

        int numberOfThreads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        AtomicInteger successfulPurchases = new AtomicInteger(0);
        AtomicInteger failedPurchases = new AtomicInteger(0);

        for(int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    productService.buyProduct(productId, 1);
                    successfulPurchases.incrementAndGet();
                } catch (Exception e) {
                    failedPurchases.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        Product updatedProduct = productRepository.findById(productId).get();
        System.out.println("Final stock: " + updatedProduct.getStock());
        System.out.println("Successful purchases: " + successfulPurchases.get());
        System.out.println("Failed purchases: " + failedPurchases.get());

        assertEquals(0, updatedProduct.getStock(), "Stock should not go below zero");
        assertEquals(1, successfulPurchases.get(), "Only one purchase should succeed");
    }
}
