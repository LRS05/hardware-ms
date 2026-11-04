package com.hardwarehub.product_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record StockReservationRequestDTO(

        @NotEmpty(message = "Items are required.")
        List<@NotNull @Valid ItemReservationDTO> items
) {
}
