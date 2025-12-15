package com.shop.inventoryservice.kafka;

import com.shop.inventoryservice.entity.Product;
import com.shop.inventoryservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka-listener, который обрабатывает события о создании заказа
 * ({@link OrderCreatedEvent}) и резервирует товары на складе.
 * <p>
 * Поведение:
 * <ul>
 *     <li>Слушает топик {@code app.kafka.orders-topic}.</li>
 *     <li>Для каждого товара из заказа уменьшает поле {@code quantity} в БД.</li>
 *     <li>Если товара не хватает — кидает {@link IllegalStateException} и логирует ошибку.</li>
 * </ul>
 * Это часть паттерна “order-service → outbox → Kafka → inventory-service”.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryOrderListener {

    private final ProductRepository productRepository;

    /**
     * Обработка события {@link OrderCreatedEvent} из Kafka.
     * <p>
     * Выполняется в транзакции: если при резервировании какого-то товара
     * произойдёт ошибка, изменения по всем товарам будут откатены.
     *
     * @param event событие, содержащее информацию о заказе и его позициях
     */
    @KafkaListener(
            topics = "${app.kafka.orders-topic}",
            groupId = "inventory-service",
            containerFactory = "orderKafkaListenerContainerFactory"
    )
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event) {
        Long orderId = event.getOrderId();

        log.info("InventoryOrderListener: received OrderCreatedEvent, orderId={}, itemsCount={}",
                orderId, event.getItems().size());

        event.getItems().forEach(item -> {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> {
                        log.error("InventoryOrderListener: product not found, productId={}, orderId={}",
                                item.getProductId(), orderId);
                        return new IllegalStateException(
                                "Product not found in inventory: " + item.getProductId()
                        );
                    });

            int before = product.getQuantity();
            int requested = item.getQuantity();
            int after = before - requested;

            if (after < 0) {
                log.error("InventoryOrderListener: not enough stock, productId={}, before={}, requested={}, orderId={}",
                        product.getId(), before, requested, orderId);
                throw new IllegalStateException(
                        "Not enough stock for product " + product.getId()
                                + " when processing order " + orderId
                );
            }

            product.setQuantity(after);
            productRepository.save(product);

            log.info("InventoryOrderListener: productId={} quantity {} -> {} (reserved {}) for orderId={}",
                    product.getId(), before, after, requested, orderId);
        });

        log.info("InventoryOrderListener: successfully reserved items for orderId={}", orderId);
    }
}

