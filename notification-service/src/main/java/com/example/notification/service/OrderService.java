package com.example.notification.service;

import com.example.notification.dto.OrderCreatedEvent;
import com.example.notification.entity.OrderEntity;
import com.example.notification.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    public void saveOrder(OrderCreatedEvent event) {

        for (OrderCreatedEvent.Item item : event.items()) {

            OrderEntity entity = OrderEntity.builder()
                    .orderId(event.orderId())
                    .userId(event.userId())
                    .totalPrice(item.totalPrice())
                    .productId(item.productId())
                    .quantity(item.quantity())
                    .price(item.price())
                    .sale(item.sale())
                    .build();

            orderRepository.save(entity);
        }
    }

    public List<OrderEntity> getAll() {
        return orderRepository.findAll();
    }

    public List<OrderEntity> getByOrderId(Long orderId) {
        return orderRepository.findByOrderId(orderId);
    }

    public List<OrderEntity> getByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }
}


