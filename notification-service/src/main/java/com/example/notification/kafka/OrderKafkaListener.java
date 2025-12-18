package com.example.notification.kafka;

import com.example.notification.dto.OrderCreatedEvent;
import com.example.notification.filter.RequestIdFilter;
import com.example.notification.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Kafka-listener notification-service.
 *
 * <p>Дополнительно: поднимает {@code X-Request-Id} из Kafka headers в MDC,
 * чтобы логирование сохранения заказов было сквозным.
 */
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
    public void handleOrder(ConsumerRecord<String, OrderCreatedEvent> record) {
        String requestId = null;
        Header requestIdHeader = record.headers().lastHeader(RequestIdFilter.HEADER);
        if (requestIdHeader != null) {
            requestId = new String(requestIdHeader.value(), StandardCharsets.UTF_8);
        }

        if (requestId != null && !requestId.isBlank()) {
            MDC.put(RequestIdFilter.MDC_KEY, requestId);
        }

        try {
            OrderCreatedEvent event = record.value();
            if (event == null) {
                log.warn("OrderKafkaListener: received null OrderCreatedEvent");
                return;
            }

            log.info("OrderKafkaListener: received OrderCreatedEvent, orderId={}, userId={}, itemsCount={}",
                    event.orderId(), event.userId(), event.items() != null ? event.items().size() : 0);

            orderService.saveOrder(event);
        } finally {
            MDC.remove(RequestIdFilter.MDC_KEY);
        }
    }

}

