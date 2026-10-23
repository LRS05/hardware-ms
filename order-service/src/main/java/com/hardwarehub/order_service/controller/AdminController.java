package com.hardwarehub.order_service.controller;

import com.hardwarehub.order_service.dto.OrderResponseDTO;
import com.hardwarehub.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/users")
public class AdminController
{
    private final OrderService orderService;

    @GetMapping("/{userId}/orders")
    public ResponseEntity<Page<OrderResponseDTO>> getAllByUserId(@PathVariable int userId, Pageable pageable)
    {
        return ResponseEntity.ok(orderService.getAllByUserId(userId, pageable));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<OrderResponseDTO> getById(@PathVariable int id)
    {
        return ResponseEntity.ok(orderService.getById(id));
    }
}
