package com.hardwarehub.payment_service.controller;

import com.hardwarehub.payment_service.dto.PaymentLinkResponseDTO;
import com.hardwarehub.payment_service.dto.PaymentRequestDTO;
import com.hardwarehub.payment_service.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/payments")
public class InternalController
{
    private final PaymentService paymentService;

    @PostMapping("/checkout")
    public ResponseEntity<PaymentLinkResponseDTO> createCheckout(@RequestBody PaymentRequestDTO requestDTO)
    {
        return ResponseEntity.ok(paymentService.createCheckout(requestDTO));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelCheckout(@PathVariable int orderId)
    {
        paymentService.cancelCheckout(orderId);
        return ResponseEntity.noContent().build();
    }
}
