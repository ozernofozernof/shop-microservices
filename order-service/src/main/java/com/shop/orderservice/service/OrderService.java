package com.shop.orderservice.service;

import com.shop.orderservice.entity.Order;
import com.shop.orderservice.entity.OrderItem;
import com.shop.orderservice.grpc.InventoryClient;
import com.shop.orderservice.kafka.OrderCreatedEvent;
import com.shop.orderservice.kafka.OrderEventProducer;
import com.shop.orderservice.dto.OrderCreateRequest;
import com.shop.orderservice.dto.OrderItemRequest;
import com.shop.orderservice.dto.OrderResponse;
import com.shop.orderservice.entity.User;
import com.shop.orderservice.repository.OrderRepository;
import com.shop.orderservice.repository.UserRepository;
import com.shop.proto.inventory.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final InventoryClient inventoryClient;
    private final OrderEventProducer orderEventProducer;

    @Transactional
    public OrderResponse createOrder(OrderCreateRequest request) {

        // 1. Получаем пользователя
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalOrderPrice = BigDecimal.ZERO;

        // 2. Обрабатываем каждый товар
        for (OrderItemRequest itemRequest : request.getItems()) {

            ProductResponse productInfo = inventoryClient.checkAvailability(
                    itemRequest.getProductId(),
                    itemRequest.getQuantity()
            );

            if (productInfo == null) {
                throw new IllegalStateException("Product not found: " + itemRequest.getProductId());
            }

            // 3. Проверка остатков
            if (productInfo.getAvailableQuantity() < itemRequest.getQuantity()) {
                throw new IllegalArgumentException(
                        "Not enough stock for product: " + productInfo.getProductId()
                );
            }

            // 4. Корректное вычисление скидки
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

            // 5. Формируем OrderItem
            OrderItem orderItem = OrderItem.builder()
                    .productId(productInfo.getProductId())
                    .quantity(itemRequest.getQuantity())
                    .price(price)
                    .sale(salePercent)
                    .totalPrice(lineTotal)
                    .build();

            orderItems.add(orderItem);
        }

        // 6. Создание заказа
        Order order = Order.builder()
                .user(user)
                .totalPrice(totalOrderPrice)
                .createdAt(OffsetDateTime.now())
                .build();

        orderItems.forEach(item -> item.setOrder(order));
        order.setItems(orderItems);

        Order saved = orderRepository.save(order);

        // 7. Формирование Kafka-события
        List<OrderCreatedEvent.Item> eventItems = new ArrayList<>();
        for (OrderItem item : saved.getItems()) {
            eventItems.add(new OrderCreatedEvent.Item(
                    item.getProductId(),
                    item.getQuantity(),
                    item.getPrice(),
                    item.getSale(),
                    item.getTotalPrice()
            ));
        }

        OrderCreatedEvent event = new OrderCreatedEvent(
                saved.getId(),
                saved.getUser().getId(),
                saved.getCreatedAt(),
                saved.getTotalPrice(),
                eventItems
        );

        orderEventProducer.send(event);

        // 8. Формирование ответа
        List<OrderResponse.Item> responseItems = new ArrayList<>();
        for (OrderItem item : saved.getItems()) {
            responseItems.add(new OrderResponse.Item(
                    item.getProductId(),
                    item.getQuantity(),
                    item.getPrice(),
                    item.getSale(),
                    item.getTotalPrice()
            ));
        }

        return new OrderResponse(
                saved.getId(),
                saved.getUser().getId(),
                saved.getTotalPrice(),
                saved.getCreatedAt(),
                responseItems
        );
    }

}
