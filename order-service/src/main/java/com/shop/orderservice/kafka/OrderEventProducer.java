package com.shop.orderservice.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    private final String topic = "orders";

    public void send(OrderCreatedEvent event) {
        kafkaTemplate.send(topic, event.getOrderId().toString(), event);
    }
}

