package com.shop.orderservice.order.dto;

import lombok.Data;

import java.util.List;

@Data
public class OrderCreateRequest {
    private List<OrderItemRequest> items;
}
