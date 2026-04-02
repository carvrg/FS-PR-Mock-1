package com.example.orders.controller;

import com.example.orders.dto.OrderSummaryDto;
import com.example.orders.model.Order;
import com.example.orders.model.OrderItem;
import com.example.orders.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Interview Problem #1 — Spring Boot / REST Controller
 * Difficulty: Medium | Issues to find: 2
 *
 * This endpoint fetches an order summary for a given customer.
 * It works correctly in local testing and all unit tests pass.
 * Find 2 things that are wrong or should be refactored.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/{customerId}/summary")
    public ResponseEntity<OrderSummaryDto> getOrderSummary(
            @PathVariable Long customerId) {

        List<Order> orders = orderService.findByCustomerId(customerId);

        double total = 0;
        for (Order order : orders) {
            List<OrderItem> items = orderService.findItemsByOrderId(order.getId());
            total += items.stream()
                          .mapToDouble(OrderItem::getPrice)
                          .sum();
        }

        return ResponseEntity.ok(new OrderSummaryDto(customerId, total));
    }
}
