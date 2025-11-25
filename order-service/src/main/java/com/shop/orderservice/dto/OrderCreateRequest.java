package com.shop.orderservice.dto;

import lombok.Data;

import java.util.List;

@Data
public class OrderCreateRequest {
    private List<OrderItemRequest> items;
}
