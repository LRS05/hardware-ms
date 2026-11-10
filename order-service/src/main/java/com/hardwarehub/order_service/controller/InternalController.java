package com.hardwarehub.order_service.controller;

import com.hardwarehub.order_service.dto.OrderResponseDTO;
import com.hardwarehub.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponseDTO> getById(@PathVariable int orderId, @RequestParam int userId)
    {
        return ResponseEntity.ok(orderService.getById(orderId, userId));
    }
}
