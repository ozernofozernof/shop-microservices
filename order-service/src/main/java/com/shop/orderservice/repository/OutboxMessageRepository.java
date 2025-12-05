package com.shop.orderservice.repository;

import com.shop.orderservice.entity.OutboxMessage;
import com.shop.orderservice.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    List<OutboxMessage> findTop100ByStatusOrderByCreatedAt(OutboxStatus status);
}
