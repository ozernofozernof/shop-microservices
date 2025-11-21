package com.example.notification.order;

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
