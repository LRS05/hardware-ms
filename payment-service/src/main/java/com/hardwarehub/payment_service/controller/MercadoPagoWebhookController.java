package com.hardwarehub.payment_service.controller;

import com.hardwarehub.payment_service.security.MercadoPagoWebhookSignatureValidator;
import com.hardwarehub.payment_service.service.MercadoPagoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments/webhooks/mercado-pago")
public class MercadoPagoWebhookController
{
    private final MercadoPagoWebhookSignatureValidator validator;
    private final MercadoPagoService mercadoPagoService;

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestParam("data.id") String dataId,
            @RequestParam("type") String type,
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestHeader(value = "x-request-id", required = false) String requestId
    )
    {
        if (!validator.isValid(dataId, type, signature, requestId))
        {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        mercadoPagoService.processNotification(dataId);

        return ResponseEntity.ok().build();
    }
}
