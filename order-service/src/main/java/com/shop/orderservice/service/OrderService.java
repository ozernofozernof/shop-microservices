package com.shop.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.orderservice.dto.OrderCreateRequest;
import com.shop.orderservice.dto.OrderItemRequest;
import com.shop.orderservice.dto.OrderResponse;
import com.shop.orderservice.entity.*;
import com.shop.orderservice.grpc.InventoryClient;
import com.shop.orderservice.kafka.OrderCreatedEvent;
import com.shop.orderservice.mapper.OrderMapper;
import com.shop.orderservice.repository.OrderRepository;
import com.shop.orderservice.repository.OutboxMessageRepository;
import com.shop.orderservice.repository.UserRepository;
import com.shop.proto.inventory.ProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.shop.orderservice.filter.RequestIdFilter;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Сервис для работы с заказами.
 * <p>
 * Основные задачи:
 * <ul>
 *     <li>Создание заказа для конкретного пользователя.</li>
 *     <li>Проверка остатков и цен в {@code inventory-service} через gRPC.</li>
 *     <li>Расчёт итоговой стоимости с учётом скидок.</li>
 *     <li>Сохранение заказа в БД.</li>
 *     <li>Запись события {@link OrderCreatedEvent} в outbox-таблицу
 *     для последующей отправки в Kafka (паттерн Outbox).</li>
 * </ul>
 *
 * Вся операция создания заказа обёрнута в транзакцию:
 * либо создаётся и заказ, и запись в outbox, либо не создаётся ничего.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final InventoryClient inventoryClient;
    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    /**
     * Создаёт новый заказ для пользователя.
     * <p>
     * Шаги:
     * <ol>
     *     <li>Проверка, что в заказе есть хотя бы одна позиция.</li>
     *     <li>Поиск пользователя по имени.</li>
     *     <li>Один батч-запрос в inventory-service для получения информации по товарам.</li>
     *     <li>Проверка остатков и расчёт стоимости каждой позиции с учётом скидки.</li>
     *     <li>Подсчёт общей стоимости заказа.</li>
     *     <li>Сохранение {@link Order} и его {@link OrderItem} в БД.</li>
     *     <li>Формирование {@link OrderCreatedEvent}, сериализация в JSON и сохранение
     *     в таблицу {@link OutboxMessage} со статусом {@link OutboxStatus#NEW}.</li>
     *     <li>Маппинг доменной модели в DTO {@link OrderResponse} и возврат клиенту.</li>
     * </ol>
     *
     * @param request  DTO c позициями заказа
     * @param username имя пользователя, для которого создаётся заказ
     * @return DTO с данными созданного заказа
     * @throws IllegalArgumentException если список товаров пустой или невалиден
     * @throws IllegalStateException    если пользователь не найден, inventory вернул
     *                                  некорректные данные или не удалось сериализовать событие
     */
    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request, String username) {

        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        int itemsCount = request.getItems().size();

        log.info("OrderService: createOrder started, username={}, itemsCount={}",
                username, itemsCount
        );

        // 1. Проверяем, что пользователь существует
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("OrderService: user '{}' not found", username);
                    return new IllegalStateException("User not found");
                });

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalOrderPrice = BigDecimal.ZERO;

        // 2. Идём в inventory один раз (batсh-запрос)
        List<OrderItemRequest> itemRequests = request.getItems();

        log.info("OrderService: sending inventory check for {} items", itemsCount);
        Map<Long, ProductResponse> inventoryInfo =
                inventoryClient.checkAvailabilityBatch(itemRequests);

        if (inventoryInfo == null || inventoryInfo.isEmpty()) {
            log.error("OrderService: inventory response is empty for {} items", itemsCount);
            throw new IllegalStateException("Inventory service returned no product data");
        }

        log.info("OrderService: inventory response contains {} products", inventoryInfo.size());

        // 3. Обрабатываем каждую позицию заказа
        for (OrderItemRequest itemRequest : itemRequests) {

            ProductResponse productInfo = inventoryInfo.get(itemRequest.getProductId());

            if (productInfo == null) {
                log.error("OrderService: product not found in inventory, productId={}",
                        itemRequest.getProductId());
                throw new IllegalStateException("Product not found: " + itemRequest.getProductId());
            }

            if (productInfo.getAvailableQuantity() < itemRequest.getQuantity()) {
                log.warn("OrderService: not enough stock, productId={}, requested={}, available={}",
                        productInfo.getProductId(),
                        itemRequest.getQuantity(),
                        productInfo.getAvailableQuantity());
                throw new IllegalArgumentException(
                        "Not enough stock for product: " + productInfo.getProductId()
                );
            }

            BigDecimal price = BigDecimal.valueOf(productInfo.getPrice());
            BigDecimal salePercent = BigDecimal.valueOf(productInfo.getSale());

            BigDecimal lineTotal = price
                    .multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

            // Применяем скидку, если она есть
            if (salePercent.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal discount = lineTotal.multiply(salePercent)
                        .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);
                lineTotal = lineTotal.subtract(discount);
            }

            totalOrderPrice = totalOrderPrice.add(lineTotal);

            OrderItem orderItem = OrderItem.builder()
                    .productId(productInfo.getProductId())
                    .quantity(itemRequest.getQuantity())
                    .price(price)
                    .sale(salePercent)
                    .totalPrice(lineTotal)
                    .build();

            log.debug("OrderService: prepared OrderItem productId={}, quantity={}, price={}, salePercent={}, lineTotal={}",
                    orderItem.getProductId(), orderItem.getQuantity(),
                    orderItem.getPrice(), orderItem.getSale(), orderItem.getTotalPrice());

            orderItems.add(orderItem);
        }

        // 4. Создание и сохранение заказа
        Order order = Order.builder()
                .user(user)
                .totalPrice(totalOrderPrice)
                .createdAt(OffsetDateTime.now())
                .status(OrderStatus.PENDING)
                .build();

        orderItems.forEach(item -> item.setOrder(order));
        order.setItems(orderItems);

        log.info("OrderService: saving order (PENDING), username={}, totalPrice={}, itemsCount={}",
                user.getUsername(), totalOrderPrice, orderItems.size());

        Order saved = orderRepository.save(order);

        log.info("OrderService: order saved, orderId={}, status={}, totalPrice={}",
                saved.getId(), saved.getStatus(), saved.getTotalPrice());

        // 5. Кладём событие в outbox
        OrderCreatedEvent event = OrderMapper.toOrderCreatedEvent(saved);
        String payload = toJson(event);
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);

        OutboxMessage outboxMessage = OutboxMessage.builder()
                .aggregateType("ORDER")
                .requestId(requestId)
                .aggregateId(saved.getId())
                .type("ORDER_CREATED")
                .payload(payload)
                .status(OutboxStatus.NEW)
                .createdAt(OffsetDateTime.now())
                .build();

        OutboxMessage savedOutbox = outboxMessageRepository.save(outboxMessage);

        log.info("OrderService: outbox message created, outboxId={}, orderId={}, status={}",
                savedOutbox.getId(), saved.getId(), savedOutbox.getStatus());

        // 6. Ответ клиенту
        OrderResponse response = OrderMapper.toOrderResponse(saved);
        log.info("OrderService: createOrder finished, orderId={}, userId={}, totalPrice={}",
                response.getOrderId(), response.getUserId(), response.getTotalPrice());

        return response;
    }

    /**
     * Утилитный метод сериализации объекта в JSON через {@link ObjectMapper}.
     *
     * @param value объект (обычно {@link OrderCreatedEvent}), который нужно превратить в JSON
     * @return строка JSON
     * @throws IllegalStateException если сериализация завершилась ошибкой
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("OrderService: failed to serialize event to JSON", e);
            throw new IllegalStateException("Failed to serialize OrderCreatedEvent", e);
        }
    }
}

