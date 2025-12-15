package com.example.notification.kafka;

import com.example.notification.dto.OrderCreatedEvent;
import com.example.notification.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka-listener для событий о созданных заказах.
 * <p>
 * Слушает топик с заказами и передаёт события в {@link OrderService}
 * для сохранения в БД (notification-service).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderKafkaListener {

    private final OrderService orderService;

    /**
     * Обработка события {@link OrderCreatedEvent} из Kafka.
     *
     * @param event десериализованное событие о созданном заказе
     */
    @KafkaListener(
            topics = "${app.kafka.orders-topic}",
            groupId = "notification-service",
            containerFactory = "eventKafkaListenerContainerFactory"
    )
    public void handleOrder(@Payload OrderCreatedEvent event) {
        log.info("OrderKafkaListener: received OrderCreatedEvent, orderId={}, userId={}, itemsCount={}",
                event.orderId(), event.userId(), event.items() != null ? event.items().size() : 0);

        orderService.saveOrder(event);
    }
}

