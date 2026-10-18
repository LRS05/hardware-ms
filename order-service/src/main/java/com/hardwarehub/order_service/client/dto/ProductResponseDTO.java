package com.hardwarehub.order_service.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponseDTO(

        int id,

        String status,

        String category,

        String sku,

        String name,

        String description,

        BigDecimal price,

        Integer stock,

        @JsonProperty("created_at")
        LocalDateTime createdAt,

        @JsonProperty("updated_at")
        LocalDateTime updatedAt
) {
}
