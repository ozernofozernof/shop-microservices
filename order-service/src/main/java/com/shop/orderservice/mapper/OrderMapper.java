package com.shop.orderservice.mapper;

import com.shop.orderservice.dto.OrderResponse;
import com.shop.orderservice.entity.Order;
import com.shop.orderservice.kafka.OrderCreatedEvent;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Утилитный класс для маппинга доменной модели заказов
 * в DTO и события для Kafka.
 * <p>
 * Содержит только статические методы и не предполагает инстанцирования.
 */
public class OrderMapper {

    private OrderMapper() {
        // utility class
    }

    /**
     * Преобразует {@link Order} в событие {@link OrderCreatedEvent},
     * которое будет отправлено в Kafka (через Outbox).
     *
     * @param order сущность заказа из БД
     * @return событие с необходимыми данными для дальнейшей обработки
     */
    public static OrderCreatedEvent toOrderCreatedEvent(Order order) {
        List<OrderCreatedEvent.Item> eventItems = order.getItems().stream()
                .map(item -> new OrderCreatedEvent.Item(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getSale(),
                        item.getTotalPrice()
                ))
                .collect(Collectors.toList());

        return new OrderCreatedEvent(
                order.getId(),
                order.getUser().getId(),
                order.getCreatedAt(),
                order.getTotalPrice(),
                eventItems
        );
    }

    /**
     * Преобразует {@link Order} в DTO {@link OrderResponse},
     * который возвращается клиенту REST-контроллера.
     *
     * @param order сущность заказа
     * @return DTO с данными по заказу, готовое к сериализации в JSON
     */
    public static OrderResponse toOrderResponse(Order order) {
        List<OrderResponse.Item> responseItems = order.getItems().stream()
                .map(item -> new OrderResponse.Item(
                        item.getProductId(),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getSale(),
                        item.getTotalPrice()
                ))
                .collect(Collectors.toList());

        String status = order.getStatus() != null
                ? order.getStatus().name()
                : "PENDING"; // если вдруг старые данные без статуса

        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getTotalPrice(),
                order.getCreatedAt(),
                status,
                responseItems
        );
    }
}
