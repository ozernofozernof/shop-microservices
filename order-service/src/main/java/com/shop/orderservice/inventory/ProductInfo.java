package com.shop.orderservice.inventory;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class ProductInfo {
    private Long productId;
    private String name;
    private Integer availableQuantity;
    private BigDecimal price;
    private BigDecimal sale;
}

