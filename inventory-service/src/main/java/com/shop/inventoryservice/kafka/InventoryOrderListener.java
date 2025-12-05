package com.shop.inventoryservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.inventoryservice.entity.Product;
import com.shop.inventoryservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryOrderListener {

    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.order-status-topic}")
    private String orderStatusTopic;

    @KafkaListener(
            topics = "${app.kafka.orders-topic}",
            groupId = "inventory-service"
    )
    @Transactional
    public void handleOrderCreated(String payload) {
        try {
            log.info("Inventory: raw payload={}", payload);

            OrderCreatedEvent event =
                    objectMapper.readValue(payload, OrderCreatedEvent.class);

            log.info("Inventory: parsed OrderCreatedEvent orderId={}, items={}",
                    event.getOrderId(), event.getItems().size());

            event.getItems().forEach(item -> {
                Product product = productRepository.findById(item.getProductId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Product not found in inventory: " + item.getProductId()
                        ));

                int before = product.getQuantity();
                int requested = item.getQuantity();
                int after = before - requested;

                if (after < 0) {
                    throw new IllegalStateException(
                            "Not enough stock for product " + product.getId()
                                    + " when processing order " + event.getOrderId()
                    );
                }

                product.setQuantity(after);
                productRepository.save(product);

                log.info("Inventory: productId={} quantity {} -> {} (reserved {}) for orderId={}",
                        product.getId(), before, after, requested, event.getOrderId());
            });

            log.info("Inventory: successfully reserved items for orderId={}", event.getOrderId());

            //успех - CONFIRMED
            sendStatusEvent(event.getOrderId(), "CONFIRMED", null);

        } catch (Exception e) {
            log.error("Inventory: failed to process payload={}", payload, e);

            //ошибка - REJECTED
            try {
                OrderCreatedEvent event =
                        objectMapper.readValue(payload, OrderCreatedEvent.class);
                sendStatusEvent(event.getOrderId(), "REJECTED", e.getMessage());
            } catch (Exception ex) {
                log.error("Inventory: additionally failed to parse payload for status event", ex);
            }
            //не пробрасываем исключение, чтобы Kafka не зацикливал переработку
        }
    }

    private void sendStatusEvent(Long orderId, String status, String reason) {
        try {
            OrderStatusEvent statusEvent = new OrderStatusEvent(orderId, status, reason);
            String json = objectMapper.writeValueAsString(statusEvent);

            kafkaTemplate.send(orderStatusTopic, orderId.toString(), json);

            log.info("Inventory: sent OrderStatusEvent orderId={}, status={}, reason={}",
                    orderId, status, reason);
        } catch (Exception e) {
            log.error("Inventory: failed to send OrderStatusEvent for orderId={}", orderId, e);
        }
    }
}

