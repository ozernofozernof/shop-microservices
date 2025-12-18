package com.shop.inventoryservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.inventoryservice.filter.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Публикатор событий изменения статуса заказа в Kafka (топик order-status).
 *
 * <p>Назначение:
 * <ul>
 *   <li>Отправлять {@link OrderStatusEvent} в виде JSON-строки.</li>
 *   <li>Прокидывать {@code X-Request-Id} в Kafka headers для сквозной трассировки.</li>
 * </ul>
 *
 * <p>Событие потребляет {@code order-service} (listener принимает String payload).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderStatusPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Топик статусов заказов.
     */
    @Value("${app.kafka.order-status-topic}")
    private String orderStatusTopic;

    /**
     * Публикует событие статуса заказа.
     *
     * @param orderId   id заказа
     * @param status    строковый статус (например, CONFIRMED/REJECTED)
     * @param reason    причина (может быть null)
     * @param requestId requestId для correlation (может быть null)
     */
    public void publish(Long orderId, String status, String reason, String requestId) {
        if (orderId == null) {
            log.warn("OrderStatusPublisher: skip publish because orderId is null");
            return;
        }
        if (status == null || status.isBlank()) {
            log.warn("OrderStatusPublisher: skip publish because status is blank, orderId={}", orderId);
            return;
        }

        try {
            OrderStatusEvent orderStatusEvent = new OrderStatusEvent(orderId, status, reason);
            String payload = objectMapper.writeValueAsString(orderStatusEvent);

            ProducerRecord<String, String> producerRecord =
                    new ProducerRecord<>(orderStatusTopic, orderId.toString(), payload);

            if (requestId != null && !requestId.isBlank()) {
                producerRecord.headers().add(
                        RequestIdFilter.HEADER,
                        requestId.getBytes(StandardCharsets.UTF_8)
                );
            }

            kafkaTemplate.send(producerRecord);

            log.info("OrderStatusPublisher: published status event, orderId={}, status={}, reason={}",
                    orderId, status, reason);

        } catch (Exception exception) {
            log.error("OrderStatusPublisher: failed to publish status event, orderId={}, status={}",
                    orderId, status, exception);
        }
    }
}


