package com.example.notification.order;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderReadController {

    private final OrderService orderService;

    @GetMapping("/all")
    public List<OrderEntity> getAll() {
        return orderService.getAll();
    }

    @GetMapping("/by-order/{orderId}")
    public List<OrderEntity> getByOrderId(@PathVariable Long orderId) {
        return orderService.getByOrderId(orderId);
    }

    @GetMapping("/by-user/{userId}")
    public List<OrderEntity> getByUserId(@PathVariable Long userId) {
        return orderService.getByUserId(userId);
    }
}
