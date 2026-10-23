package com.hardwarehub.product_service.dto;

import com.hardwarehub.product_service.enums.Category;
import com.hardwarehub.product_service.enums.ProductStatus;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductUpdateRequestDTO(

        ProductStatus status,

        Category category,

        @Size(max = 64, message = "SKU must not exceed 64 characters.")
        String sku,

        @Size(max = 150, message = "Name must not exceed 150 characters.")
        String name,

        @Size(max = 300, message = "Description must not exceed 300 characters.")
        String description,

        @Positive(message = "Price must be greater than zero.")
        BigDecimal price,

        @Positive(message = "Stock must be greater than zero.")
        Integer stock
) {
}
