package com.example.notification.service;

import com.example.notification.dto.OrderCreatedEvent;
import com.example.notification.entity.OrderEntity;
import com.example.notification.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Сервис уведомлений/логирования заказов в notification-service.
 * <p>
 * Основные задачи:
 * <ul>
 *     <li>сохранять данные о заказах, полученных из Kafka;</li>
 *     <li>давать выборки по заказам и пользователям.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;

    /**
     * Сохраняет заказ, полученный из Kafka-события.
     * <p>
     * Каждая позиция заказа (item) превращается в отдельную запись {@link OrderEntity},
     * чтобы потом можно было удобно фильтровать по orderId / userId / productId.
     *
     * @param event событие о созданном заказе
     */
    public void saveOrder(OrderCreatedEvent event) {

        if (event.items() == null || event.items().isEmpty()) {
            log.warn("OrderService: received OrderCreatedEvent with no items, orderId={}", event.orderId());
            return;
        }

        log.info("OrderService: saving orderId={} for userId={}, itemsCount={}",
                event.orderId(), event.userId(), event.items().size());

        for (OrderCreatedEvent.Item item : event.items()) {

            OrderEntity entity = OrderEntity.builder()
                    .orderId(event.orderId())
                    .userId(event.userId())
                    .totalPrice(item.totalPrice())
                    .productId(item.productId())
                    .quantity(item.quantity())
                    .price(item.price())
                    .sale(item.sale())
                    .build();

            orderRepository.save(entity);
        }

        log.info("OrderService: orderId={} saved successfully ({} items)",
                event.orderId(), event.items().size());
    }

    /**
     * Возвращает все сохранённые записи о заказах.
     *
     * @return список всех записей {@link OrderEntity}
     */
    public List<OrderEntity> getAll() {
        return orderRepository.findAll();
    }

    /**
     * Возвращает все записи по указанному идентификатору заказа.
     *
     * @param orderId идентификатор заказа
     * @return список записей по данному orderId
     */
    public List<OrderEntity> getByOrderId(Long orderId) {
        return orderRepository.findByOrderId(orderId);
    }

    /**
     * Возвращает все записи по указанному идентификатору пользователя.
     *
     * @param userId идентификатор пользователя
     * @return список записей по данному userId
     */
    public List<OrderEntity> getByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }
}


