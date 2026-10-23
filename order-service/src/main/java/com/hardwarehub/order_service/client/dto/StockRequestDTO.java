package com.hardwarehub.order_service.client.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StockRequestDTO(

        @NotNull(message = "Stock is required.")
        @Positive(message = "Stock must be greater than zero.")
        Integer stock
) {
}
