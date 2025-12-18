package com.shop.inventoryservice.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderCreatedEvent {

    private Long orderId;
    private Long userId;
    private OffsetDateTime createdAt;
    private BigDecimal totalPrice;
    private List<Item> items;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Item {
        private Long productId;
        private Integer quantity;
        private BigDecimal price;
        private BigDecimal sale;
        private BigDecimal totalPrice;
    }
}

