package com.hardwarehub.order_service.dto;

import com.hardwarehub.order_service.enums.PaymentProvider;
import jakarta.validation.constraints.NotNull;

public record CreateCheckoutRequestDTO(

        @NotNull(message = "Payment provider is required.")
        PaymentProvider provider
) {
}
