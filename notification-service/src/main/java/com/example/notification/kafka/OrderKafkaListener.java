package com.example.notification.kafka;

import com.example.notification.dto.OrderCreatedEvent;
import com.example.notification.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderKafkaListener {

    private final OrderService orderService;

    @KafkaListener(
            topics = "${app.kafka.orders-topic}",
            groupId = "notification-service",
            containerFactory = "eventKafkaListenerContainerFactory"
    )
    public void handleOrder(@Payload OrderCreatedEvent event) {
        log.info("Received order: {}", event);
        orderService.saveOrder(event);
    }
}

