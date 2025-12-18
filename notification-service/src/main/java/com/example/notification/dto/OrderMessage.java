package com.example.notification.dto;

import java.math.BigDecimal;

public record OrderMessage(
        Long orderId,
        Long productId,
        Integer quantity,
        BigDecimal price,
        Double sale,
        BigDecimal totalPrice,
        Long userId
) {
}
