package com.shop.orderservice.controller;

import com.shop.orderservice.service.OrderService;
import com.shop.orderservice.dto.OrderCreateRequest;
import com.shop.orderservice.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST-контроллер для работы с заказами.
 * <p>
 * Сейчас отвечает за:
 * <ul>
 *     <li>Создание нового заказа по endpoint'у {@code POST /api/orders}.</li>
 * </ul>
 * <p>
 * Имя пользователя не передаётся в теле запроса, а приходит через заголовок
 * {@code X-User-Name}, который добавляет API Gateway на основе валидного JWT.
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Создаёт новый заказ для аутентифицированного пользователя.
     *
     * @param request  DTO с позициями заказа
     * @param username имя пользователя, проброшенное из API Gateway
     *                 через заголовок {@code X-User-Name}
     * @return DTO с информацией о созданном заказе
     * @throws IllegalStateException если заголовок {@code X-User-Name}
     *                               отсутствует или пустой — значит,
     *                               запрос пришёл не через gateway или
     *                               фильтры настроены некорректно
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody OrderCreateRequest request,
            @RequestHeader(value = "X-User-Name", required = false) String username
    ) {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("Missing X-User-Name header");
        }

        log.info("OrderController: POST /api/orders called, user={}, itemsCount={}",
                username,
                request.getItems() != null ? request.getItems().size() : 0
        );

        OrderResponse response = orderService.createOrder(request, username);

        log.info("OrderController: order created successfully, orderId={}, userId={}, totalPrice={}, itemsCount={}",
                response.getOrderId(),
                response.getUserId(),
                response.getTotalPrice(),
                response.getItems() != null ? response.getItems().size() : 0
        );

        return ResponseEntity.ok(response);
    }
}


