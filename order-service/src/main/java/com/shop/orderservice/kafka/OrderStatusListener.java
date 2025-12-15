package com.shop.orderservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.orderservice.entity.Order;
import com.shop.orderservice.entity.OrderStatus;
import com.shop.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Слушатель Kafka-событий об изменении статуса заказа.
 * <p>
 * Ожидает сообщения из топика {@code app.kafka.order-status-topic},
 * десериализует их в {@link OrderStatusEvent} и обновляет статус
 * соответствующего {@link Order} в БД.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderStatusListener {

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    /**
     * Обработчик сообщений со статусом заказа.
     * <p>
     * Шаги:
     * <ol>
     *     <li>Десериализует входной JSON в {@link OrderStatusEvent}.</li>
     *     <li>Ищет заказ в БД по {@code orderId}.</li>
     *     <li>В зависимости от статуса из события ({@code CONFIRMED}/{@code REJECTED})
     *     обновляет поле {@link OrderStatus}.</li>
     *     <li>Логирует предыдущее и новое состояние.</li>
     * </ol>
     * Если статус неизвестен или заказа нет — пишет в лог и завершает обработку.
     *
     * @param payload JSON-строка с данными события
     */
    @KafkaListener(
            topics = "${app.kafka.order-status-topic}",
            groupId = "order-service"
    )
    @Transactional
    public void handleStatus(String payload) {
        try {
            OrderStatusEvent event =
                    objectMapper.readValue(payload, OrderStatusEvent.class);

            log.info("OrderStatusListener: received status event, orderId={}, status={}, reason={}",
                    event.getOrderId(), event.getStatus(), event.getReason());

            Order order = orderRepository.findById(event.getOrderId())
                    .orElseThrow(() -> {
                        log.error("OrderStatusListener: order not found, orderId={}", event.getOrderId());
                        return new IllegalStateException("Order not found: " + event.getOrderId());
                    });

            OrderStatus previousStatus = order.getStatus();

            String status = event.getStatus();
            if ("CONFIRMED".equalsIgnoreCase(status)) {
                order.setStatus(OrderStatus.CONFIRMED);
            } else if ("REJECTED".equalsIgnoreCase(status)) {
                order.setStatus(OrderStatus.REJECTED);
            } else {
                log.warn("OrderStatusListener: unknown status '{}' for orderId={}",
                        status, event.getOrderId());
                return;
            }

            log.info("OrderStatusListener: updated order status, orderId={}, previousStatus={}, newStatus={}, reason={}",
                    event.getOrderId(), previousStatus, order.getStatus(), event.getReason());

        } catch (Exception e) {
            log.error("OrderStatusListener: failed to process payload={}", payload, e);
        }
    }
}


