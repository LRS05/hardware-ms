package com.hardwarehub.order_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hardwarehub.order_service.enums.PaymentProvider;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentRequestDTO(

        @JsonProperty("order_id")
        int orderId,

        BigDecimal amount,

        PaymentProvider provider,

        @JsonProperty("expires_at")
        Instant expiresAt
) {
}
