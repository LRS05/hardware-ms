package com.hardwarehub.product_service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ItemReservationDTO(

        @NotNull(message = "Product id is required.")
        @Positive(message = "Product id must be greater than zero.")
        Integer productId,

        @NotNull(message = "Quantity is required.")
        @Positive(message = "Quantity must be greater than zero.")
        Integer quantity
) {
}
