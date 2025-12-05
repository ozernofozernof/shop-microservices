package com.shop.inventoryservice.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderStatusEvent {

    private Long orderId;
    private String status;  // "CONFIRMED" или "REJECTED"
    private String reason;
}
