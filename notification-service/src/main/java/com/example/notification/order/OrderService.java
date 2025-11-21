package com.example.notification.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    public void saveFromMessage(OrderMessage msg) {
        OrderEntity entity = OrderEntity.builder()
                .orderId(msg.orderId())
                .productId(msg.productId())
                .quantity(msg.quantity())
                .price(msg.price())
                .sale(msg.sale())
                .totalPrice(msg.totalPrice())
                .userId(msg.userId())
                .build();

        orderRepository.save(entity);
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

