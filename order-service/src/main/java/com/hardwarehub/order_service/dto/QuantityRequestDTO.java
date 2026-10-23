package com.hardwarehub.order_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record QuantityRequestDTO(

        @NotNull(message = "Quantity is required.")
        @Positive(message = "Quantity must be greater than zero.")
        Integer quantity
) {
}
