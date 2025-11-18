package com.lab.inventory.dto;

import java.math.BigDecimal;

public record CreateProductRequestDTO(String name, BigDecimal price, Integer stock) {
}
