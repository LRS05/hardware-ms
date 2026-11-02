package com.hardwarehub.order_service.controller;

import com.hardwarehub.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/orders")
public class InternalController
{
    private final OrderService orderService;

    @PatchMapping("/{orderId}/complete")
    public ResponseEntity<Void> completeOrder(@PathVariable int orderId)
    {
        orderService.complete(orderId);
        return ResponseEntity.noContent().build();
    }
}
