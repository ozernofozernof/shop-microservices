package com.example.notification.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderCreatedEvent(
        Long orderId,
        Long userId,
        BigDecimal totalPrice,
        OffsetDateTime createdAt,
        List<Item> items
) {
    public record Item(
            Long productId,
            Integer quantity,
            BigDecimal price,
            Double sale,
            BigDecimal totalPrice
    ) {}
}