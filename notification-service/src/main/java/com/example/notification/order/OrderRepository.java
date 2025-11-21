package com.example.notification.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    // все записи по одному заказу
    List<OrderEntity> findByOrderId(Long orderId);

    // все записи по одному пользователю
    List<OrderEntity> findByUserId(Long userId);
}
