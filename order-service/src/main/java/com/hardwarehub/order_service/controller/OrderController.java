package com.hardwarehub.order_service.controller;

import com.hardwarehub.order_service.dto.OrderResponseDTO;
import com.hardwarehub.order_service.enums.OrderStatus;
import com.hardwarehub.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController
{
    private final OrderService orderService;

    @PatchMapping("/active/checkout")
    public ResponseEntity<OrderResponseDTO> checkout(@AuthenticationPrincipal Jwt jwt)
    {
        return ResponseEntity.ok(orderService.checkout(getUserId(jwt)));
    }

    @PatchMapping("/pending/reopen")
    public ResponseEntity<OrderResponseDTO> reopen(@AuthenticationPrincipal Jwt jwt)
    {
        return ResponseEntity.ok(orderService.reopen(getUserId(jwt)));
    }

    @PatchMapping("/active/refresh")
    public ResponseEntity<Void> refresh(@AuthenticationPrincipal Jwt jwt)
    {
        orderService.refreshActiveOrder(getUserId(jwt));

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/active/clear")
    public ResponseEntity<Void> clear(@AuthenticationPrincipal Jwt jwt)
    {
        orderService.clearActiveOrder(getUserId(jwt));

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/active")
    public ResponseEntity<OrderResponseDTO> getActive(@AuthenticationPrincipal Jwt jwt)
    {
        return ResponseEntity.ok(orderService.getActive(getUserId(jwt)));
    }

    @GetMapping("/pending")
    public ResponseEntity<OrderResponseDTO> getPending(@AuthenticationPrincipal Jwt jwt)
    {
        return ResponseEntity.ok(orderService.getPending(getUserId(jwt)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> getById(@PathVariable int id, @AuthenticationPrincipal Jwt jwt)
    {
        return ResponseEntity.ok(orderService.getById(id, getUserId(jwt)));
    }

    @GetMapping
    public ResponseEntity<Page<OrderResponseDTO>> getAll(Pageable pageable, @AuthenticationPrincipal Jwt jwt)
    {
        return ResponseEntity.ok(orderService.getAll(getUserId(jwt), pageable));
    }

    @GetMapping(params = "status")
    public ResponseEntity<Page<OrderResponseDTO>> getAllByStatus(@RequestParam OrderStatus status, Pageable pageable, @AuthenticationPrincipal Jwt jwt)
    {
        return ResponseEntity.ok(orderService.getAllByStatus(getUserId(jwt), status, pageable));
    }

    @PatchMapping("/active/items/products/{productId}")
    public ResponseEntity<Void> addItemToOrder(@PathVariable int productId, @AuthenticationPrincipal Jwt jwt)
    {
        orderService.addItem(getUserId(jwt), productId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/active/items/{itemId}")
    public ResponseEntity<Void> updateItemQuantity(@PathVariable int itemId, @RequestParam int quantity, @AuthenticationPrincipal Jwt jwt)
    {
        orderService.updateItemQuantity(itemId, getUserId(jwt), quantity);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/active/items/{itemId}")
    public ResponseEntity<Void> deleteItemById(@PathVariable int itemId, @AuthenticationPrincipal Jwt jwt)
    {
        orderService.deleteItemById(itemId, getUserId(jwt));
        return ResponseEntity.noContent().build();
    }

    private Integer getUserId(Jwt jwt)
    {
        return Integer.parseInt(jwt.getSubject());
    }
}
