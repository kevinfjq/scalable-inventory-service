package com.lab.inventory.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lab.inventory.dto.CreateProductRequestDTO;
import com.lab.inventory.model.Product;
import com.lab.inventory.service.ProductService;

import java.net.URI;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<Void> createProduct(@RequestBody CreateProductRequestDTO requestDTO) {
        Long newId = productService.createProduct(requestDTO);

        return ResponseEntity.created(URI.create("/products/" + newId)).build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable Long id) {
        Optional<Product> productOpt = productService.getProductById(id);
        if(productOpt.isPresent()) {
            return ResponseEntity.ok(productOpt.get());
        } 
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/buy")
    public ResponseEntity<String> buyProduct(@PathVariable Long id, @RequestParam int quantity) {
        productService.buyProduct(id, quantity);
        return ResponseEntity.ok("Purchase successful");
    }
    
    
}
