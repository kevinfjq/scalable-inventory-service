package com.lab.inventory.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lab.inventory.dto.CreateProductRequestDTO;
import com.lab.inventory.model.Product;
import com.lab.inventory.repository.ProductRepository;



@Service
public class ProductService {

    private final ProductRepository productRepository;

    ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
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
        return productRepository.findById(id);
        
    }
}
