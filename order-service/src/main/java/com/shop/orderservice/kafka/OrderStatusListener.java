package com.shop.orderservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.orderservice.entity.Order;
import com.shop.orderservice.entity.OrderStatus;
import com.shop.orderservice.filter.RequestIdFilter;
import com.shop.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

/**
 * Слушатель Kafka-событий об изменении статуса заказа.
 *
 * <p>Дополнительно: поднимает {@code requestId} из Kafka headers в MDC,
 * чтобы логирование было сквозным.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderStatusListener {

    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.order-status-topic}",
            groupId = "order-service"
    )
    @Transactional
    public void handleStatus(ConsumerRecord<String, String> record) {
        String requestId = null;
        Header h = record.headers().lastHeader(RequestIdFilter.HEADER);
        if (h != null) {
            requestId = new String(h.value(), StandardCharsets.UTF_8);
        }

        if (requestId != null && !requestId.isBlank()) {
            MDC.put(RequestIdFilter.MDC_KEY, requestId);
        }

        try {
            String payload = record.value();

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
            log.error("OrderStatusListener: failed to process record", e);
        } finally {
            MDC.remove(RequestIdFilter.MDC_KEY);
        }
    }
}



