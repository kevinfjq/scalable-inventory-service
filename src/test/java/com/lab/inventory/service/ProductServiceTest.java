package com.lab.inventory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import com.github.benmanes.caffeine.cache.Cache;
import com.lab.inventory.dto.CreateProductRequestDTO;
import com.lab.inventory.model.Product;
import com.lab.inventory.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {
    @Mock
    private ProductRepository productRepository;

    @Mock
    RedissonClient redissonClient;

    @Mock 
    RBucket<Object> redisBucket;
    
    @Mock
    Cache<Long, Product> localCache;

    @InjectMocks
    private ProductService productService;

    private Product testProduct;
    private CreateProductRequestDTO testRequest;

    @BeforeEach
    void setUp() {
        testProduct = Product.builder()
            .id(1L)
            .name("Test product")
            .price(BigDecimal.TEN)
            .stock(10)
            .build();

        testRequest = new CreateProductRequestDTO("Test Product", BigDecimal.TEN, 10);
    }

    @Test
    void createProduct_ShouldSaveProductAndReturnId() {
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        Long savedId = productService.createProduct(testRequest);

        assertEquals(1L, savedId);

        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void getProductById_ShouldReturnProduct() {
        when(localCache.getIfPresent(1L)).thenReturn(null);
        when(redissonClient.getBucket(anyString())).thenReturn(redisBucket);
        when(redisBucket.get()).thenReturn(null);

        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        Optional<Product> result = productService.getProductById(1L);

        assertTrue(result.isPresent());
        assertEquals("Test product", result.get().getName());

        verify(localCache, times(1)).put(eq(1L), any(Product.class));
        verify(redisBucket, times(1)).set(any(Product.class), any());
    }

    @Test
    void getProductById_WhenProductDoesNotExist_ShouldReturnEmptyOptional() {
        when(localCache.getIfPresent(anyLong())).thenReturn(null);

        when(redissonClient.getBucket(anyString())).thenReturn(redisBucket);
        when(redisBucket.get()).thenReturn(null);

        when(productRepository.findById(anyLong())).thenReturn(Optional.empty());

        Optional<Product> result = productService.getProductById(999L);

        assertFalse(result.isPresent());
    }
}
