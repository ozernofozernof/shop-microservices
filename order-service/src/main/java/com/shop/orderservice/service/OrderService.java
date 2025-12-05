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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final InventoryClient inventoryClient;

    //репозиторий outbox и ObjectMapper
    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request) {

        // 1. Получаем пользователя
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        log.info("Create order request from user={}, itemsCount={}", username, request.getItems().size());

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalOrderPrice = BigDecimal.ZERO;

        // 2. ОДИН раз идём в inventory за информацией по всем товарам
        List<OrderItemRequest> itemRequests = request.getItems();

        log.info("Sending inventory check for {} items", itemRequests.size());
        Map<Long, ProductResponse> inventoryInfo =
                inventoryClient.checkAvailabilityBatch(itemRequests);
        log.info("Inventory response contains {} products", inventoryInfo.size());

        // 3. Обрабатываем каждый товар, используя уже полученные данные
        for (OrderItemRequest itemRequest : itemRequests) {

            ProductResponse productInfo = inventoryInfo.get(itemRequest.getProductId());

            if (productInfo == null) {
                throw new IllegalStateException("Product not found: " + itemRequest.getProductId());
            }

            // Проверка остатков
            if (productInfo.getAvailableQuantity() < itemRequest.getQuantity()) {
                throw new IllegalArgumentException(
                        "Not enough stock for product: " + productInfo.getProductId()
                );
            }

            // Корректное вычисление скидки
            BigDecimal price = BigDecimal.valueOf(productInfo.getPrice());
            BigDecimal salePercent = BigDecimal.valueOf(productInfo.getSale());

            BigDecimal lineTotal = price
                    .multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

            if (salePercent.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal discount = lineTotal.multiply(salePercent)
                        .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);
                lineTotal = lineTotal.subtract(discount);
            }

            totalOrderPrice = totalOrderPrice.add(lineTotal);

            // Формируем OrderItem
            OrderItem orderItem = OrderItem.builder()
                    .productId(productInfo.getProductId())
                    .quantity(itemRequest.getQuantity())
                    .price(price)
                    .sale(salePercent)
                    .totalPrice(lineTotal)
                    .build();

            orderItems.add(orderItem);
        }

        // 4. Создание заказа
        Order order = Order.builder()
                .user(user)
                .totalPrice(totalOrderPrice)
                .createdAt(OffsetDateTime.now())
                .status(OrderStatus.PENDING)
                .build();

        orderItems.forEach(item -> item.setOrder(order));
        order.setItems(orderItems);

        Order saved = orderRepository.save(order);

        // 5. Кладём событие в outbox

        // 5.1. Собираем существующий OrderCreatedEvent через маппер
        OrderCreatedEvent event = OrderMapper.toOrderCreatedEvent(saved);

        // 5.2. Сериализуем в JSON
        String payload = toJson(event);

        // 5.3. Сохраняем OutboxMessage со статусом NEW
        OutboxMessage outboxMessage = OutboxMessage.builder()
                .aggregateType("ORDER")
                .aggregateId(saved.getId())
                .type("ORDER_CREATED")
                .payload(payload)
                .status(OutboxStatus.NEW)
                .createdAt(OffsetDateTime.now())
                .build();

        outboxMessageRepository.save(outboxMessage);


        // 6. Ответ клиенту
        return OrderMapper.toOrderResponse(saved);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize OrderCreatedEvent", e);
        }
    }
}
