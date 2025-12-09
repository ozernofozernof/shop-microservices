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

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request) {

        // 1. Пользователь
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        log.info("OrderService: createOrder started, username={}, itemsCount={}",
                username, request.getItems().size());

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("OrderService: user '{}' not found", username);
                    return new IllegalStateException("User not found");
                });

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalOrderPrice = BigDecimal.ZERO;

        // 2. Идём в inventory один раз
        List<OrderItemRequest> itemRequests = request.getItems();

        log.info("OrderService: sending inventory check for {} items", itemRequests.size());
        Map<Long, ProductResponse> inventoryInfo =
                inventoryClient.checkAvailabilityBatch(itemRequests);
        log.info("OrderService: inventory response contains {} products", inventoryInfo.size());

        // 3. Обрабатываем товары
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

        // 4. Создание заказа
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

        OutboxMessage outboxMessage = OutboxMessage.builder()
                .aggregateType("ORDER")
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.error("OrderService: failed to serialize event to JSON", e);
            throw new IllegalStateException("Failed to serialize OrderCreatedEvent", e);
        }
    }
}
