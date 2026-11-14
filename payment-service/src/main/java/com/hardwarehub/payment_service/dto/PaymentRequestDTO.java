package com.hardwarehub.payment_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hardwarehub.payment_service.enums.PaymentProvider;

import java.math.BigDecimal;

public record PaymentRequestDTO(

        @JsonProperty("order_id")
        int orderId,

        BigDecimal amount,

        PaymentProvider provider
) {
}
