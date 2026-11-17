package com.hardwarehub.product_service.dto;

import java.time.Instant;
import java.util.List;

public record StockReservationRequestDTO(

        int orderId,

        Instant expiresAt,

        List<ItemReservationDTO> items
) {
}
