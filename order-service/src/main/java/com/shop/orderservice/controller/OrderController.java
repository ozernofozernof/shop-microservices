package com.shop.orderservice.controller;

import com.shop.orderservice.service.OrderService;
import com.shop.orderservice.dto.OrderCreateRequest;
import com.shop.orderservice.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderCreateRequest request) {
        log.info(">>> CREATE ORDER CALLED");
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.ok(response);
    }
}

