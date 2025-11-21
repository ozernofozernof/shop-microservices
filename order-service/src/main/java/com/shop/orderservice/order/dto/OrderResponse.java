package com.shop.orderservice.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class OrderResponse {
    private Long orderId;
    private Long userId;
    private BigDecimal totalPrice;
    private OffsetDateTime createdAt;
    private List<Item> items;

    @Data
    @AllArgsConstructor
    public static class Item {
        private Long productId;
        private Integer quantity;
        private BigDecimal price;
        private BigDecimal sale;
        private BigDecimal totalPrice;
    }
}

