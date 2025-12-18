package com.shop.inventoryservice.kafka;

import com.shop.inventoryservice.entity.Product;
import com.shop.inventoryservice.filter.RequestIdFilter;
import com.shop.inventoryservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;

/**
 * Kafka-listener inventory-service, который обрабатывает события о создании заказа
 * ({@link OrderCreatedEvent}) и списывает (резервирует) товары на складе.
 *
 * <p>Сквозной requestId:
 * <ul>
 *   <li>Читает {@code X-Request-Id} из Kafka headers.</li>
 *   <li>Кладёт requestId в MDC под ключом {@code requestId}.</li>
 *   <li>Все логи внутри обработки будут содержать этот id.</li>
 * </ul>
 *
 * <p>Статус заказа:
 * <ul>
 *   <li>При успехе публикует {@code CONFIRMED} после commit транзакции.</li>
 *   <li>При ошибке публикует {@code REJECTED} (с reason) и бросает exception,
 *       чтобы транзакция откатилась.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryOrderListener {

    private final ProductRepository productRepository;
    private final OrderStatusPublisher orderStatusPublisher;

    @KafkaListener(
            topics = "${app.kafka.orders-topic}",
            groupId = "inventory-service",
            containerFactory = "orderKafkaListenerContainerFactory"
    )
    @Transactional
    public void handleOrderCreated(ConsumerRecord<String, OrderCreatedEvent> consumerRecord) {

        String requestId = extractRequestId(consumerRecord);
        if (requestId != null && !requestId.isBlank()) {
            MDC.put(RequestIdFilter.MDC_KEY, requestId);
        }

        try {
            OrderCreatedEvent orderCreatedEvent = consumerRecord.value();
            if (orderCreatedEvent == null) {
                log.warn("InventoryOrderListener: received null event (record value is null)");
                return;
            }

            Long orderId = orderCreatedEvent.getOrderId();
            int itemsCount = (orderCreatedEvent.getItems() != null) ? orderCreatedEvent.getItems().size() : 0;

            log.info("InventoryOrderListener: received OrderCreatedEvent, orderId={}, itemsCount={}",
                    orderId, itemsCount);

            if (orderId == null) {
                throw new IllegalStateException("OrderCreatedEvent has null orderId");
            }
            if (orderCreatedEvent.getItems() == null || orderCreatedEvent.getItems().isEmpty()) {
                throw new IllegalStateException("OrderCreatedEvent has no items");
            }

            for (OrderCreatedEvent.Item orderItem : orderCreatedEvent.getItems()) {
                Long productId = orderItem.getProductId();
                Integer requestedQuantity = orderItem.getQuantity();

                if (productId == null) {
                    throw new IllegalStateException("Order item has null productId for orderId=" + orderId);
                }
                if (requestedQuantity == null || requestedQuantity <= 0) {
                    throw new IllegalStateException("Invalid item quantity for productId=" + productId + ", orderId=" + orderId);
                }

                Product product = productRepository.findById(productId).orElse(null);
                if (product == null) {
                    log.error("InventoryOrderListener: product not found, productId={}, orderId={}", productId, orderId);
                    throw new IllegalStateException("Product not found in inventory: " + productId);
                }

                int quantityBefore = product.getQuantity();
                int quantityAfter = quantityBefore - requestedQuantity;

                if (quantityAfter < 0) {
                    log.error("InventoryOrderListener: not enough stock, productId={}, before={}, requested={}, orderId={}",
                            product.getId(), quantityBefore, requestedQuantity, orderId);
                    throw new IllegalStateException(
                            "Not enough stock for product " + product.getId() + " when processing order " + orderId
                    );
                }

                product.setQuantity(quantityAfter);
                productRepository.save(product);

                log.info("InventoryOrderListener: productId={} quantity {} -> {} (reserved {}) for orderId={}",
                        product.getId(), quantityBefore, quantityAfter, requestedQuantity, orderId);
            }

            log.info("InventoryOrderListener: successfully reserved items for orderId={}", orderId);

            // CONFIRMED публикуем после commit транзакции
            registerAfterCommitConfirmation(orderId, requestId);

        } catch (Exception exception) {
            String failureReason = (exception.getMessage() != null && !exception.getMessage().isBlank())
                    ? exception.getMessage()
                    : "Inventory error";

            // orderId можем не знать (например, если event=null). Поэтому аккуратно.
            Long orderIdForReject = null;
            OrderCreatedEvent eventValue = consumerRecord.value();
            if (eventValue != null) {
                orderIdForReject = eventValue.getOrderId();
            }

            log.error("InventoryOrderListener: failed to process orderId={}, reason={}",
                    orderIdForReject, failureReason, exception);

            if (orderIdForReject != null) {
                orderStatusPublisher.publish(orderIdForReject, "REJECTED", failureReason, requestId);
            }

            // Важно: бросаем исключение, чтобы @Transactional сделал rollback
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(exception);
        } finally {
            MDC.remove(RequestIdFilter.MDC_KEY);
        }
    }

    private String extractRequestId(ConsumerRecord<String, ?> consumerRecord) {
        Header requestIdHeader = consumerRecord.headers().lastHeader(RequestIdFilter.HEADER);
        if (requestIdHeader == null) {
            return null;
        }
        return new String(requestIdHeader.value(), StandardCharsets.UTF_8);
    }

    private void registerAfterCommitConfirmation(Long orderId, String requestId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // На всякий случай: если вдруг нет активной синхронизации — публикуем сразу.
            orderStatusPublisher.publish(orderId, "CONFIRMED", null, requestId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                orderStatusPublisher.publish(orderId, "CONFIRMED", null, requestId);
            }
        });
    }
}




