package com.shop.orderservice.order;

import com.shop.orderservice.inventory.InventoryClient;
import com.shop.orderservice.inventory.ProductInfo;
import com.shop.orderservice.kafka.OrderCreatedEvent;
import com.shop.orderservice.kafka.OrderEventProducer;
import com.shop.orderservice.order.dto.OrderCreateRequest;
import com.shop.orderservice.order.dto.OrderItemRequest;
import com.shop.orderservice.order.dto.OrderResponse;
import com.shop.orderservice.user.User;
import com.shop.orderservice.user.UserRepository;
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
        // 1. Берём текущего юзера из SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalOrderPrice = BigDecimal.ZERO;

        // 2. Для каждой позиции идём в Inventory Service (gRPC)
        for (OrderItemRequest itemRequest : request.getItems()) {
            ProductInfo productInfo = inventoryClient.checkAvailability(
                    itemRequest.getProductId(),
                    itemRequest.getQuantity()
            );

            if (productInfo.getAvailableQuantity() < itemRequest.getQuantity()) {
                throw new IllegalArgumentException("Not enough stock for product " + productInfo.getProductId());
            }

            BigDecimal price = productInfo.getPrice();
            BigDecimal sale = productInfo.getSale(); // допустим, это скидка в деньгах
            BigDecimal lineTotal = price
                    .subtract(sale != null ? sale : BigDecimal.ZERO)
                    .multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

            totalOrderPrice = totalOrderPrice.add(lineTotal);

            OrderItem orderItem = OrderItem.builder()
                    .productId(productInfo.getProductId())
                    .quantity(itemRequest.getQuantity())
                    .price(price)
                    .sale(sale)
                    .totalPrice(lineTotal)
                    .build();

            orderItems.add(orderItem);
        }

        // 3. Создаём заказ и сохраняем в БД
        Order order = Order.builder()
                .user(user)
                .totalPrice(totalOrderPrice)
                .createdAt(OffsetDateTime.now())
                .build();

        // связываем items с order
        orderItems.forEach(item -> item.setOrder(order));
        order.setItems(orderItems);

        Order saved = orderRepository.save(order);

        // 4. Отправляем событие в Kafka
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

        // 5. Формируем ответ клиенту
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

