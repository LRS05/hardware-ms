package com.hardwarehub.product_service.dto;

import com.hardwarehub.product_service.enums.Category;
import com.hardwarehub.product_service.enums.ProductStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductUpdateRequestDTO(

        ProductStatus status,

        Category category,

        @Pattern(
                regexp = "(?sU).*\\S.*",
                message = "SKU must not be blank."
        )
        @Size(max = 64, message = "SKU must not exceed 64 characters.")
        String sku,

        @Pattern(
                regexp = "(?sU).*\\S.*",
                message = "Name must not be blank."
        )
        @Size(max = 64, message = "Name must not exceed 64 characters.")
        String name,

        @Pattern(
                regexp = "(?sU).*\\S.*",
                message = "Description must not be blank."
        )
        @Size(max = 300, message = "Description must not exceed 300 characters.")
        String description,

        @Positive(message = "Price must be greater than zero.")
        BigDecimal price,

        @Positive(message = "Stock must be greater than zero.")
        Integer stock
) {
}
