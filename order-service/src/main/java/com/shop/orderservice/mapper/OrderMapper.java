package com.shop.orderservice.mapper;

import com.shop.orderservice.dto.OrderResponse;
import com.shop.orderservice.entity.Order;
import com.shop.orderservice.entity.OrderItem;
import com.shop.orderservice.kafka.OrderCreatedEvent;

import java.util.List;
import java.util.stream.Collectors;

public class OrderMapper {

    private OrderMapper() {
    }

    public static OrderCreatedEvent toOrderCreatedEvent(Order order) {
        List<OrderCreatedEvent.Item> eventItems = order.getItems().stream()
                .map(item -> new OrderCreatedEvent.Item(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getSale(),
                        item.getTotalPrice()
                ))
                .collect(Collectors.toList());

        return new OrderCreatedEvent(
                order.getId(),
                order.getUser().getId(),
                order.getCreatedAt(),
                order.getTotalPrice(),
                eventItems
        );
    }

    public static OrderResponse toOrderResponse(Order order) {
        List<OrderResponse.Item> responseItems = order.getItems().stream()
                .map(item -> new OrderResponse.Item(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getSale(),
                        item.getTotalPrice()
                ))
                .collect(Collectors.toList());

        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getTotalPrice(),
                order.getCreatedAt(),
                responseItems
        );
    }
}
