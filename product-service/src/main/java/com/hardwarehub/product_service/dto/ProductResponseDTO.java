package com.hardwarehub.product_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hardwarehub.product_service.enums.Category;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductResponseDTO(

        int id,

        Category category,

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
