package com.hardwarehub.product_service.dto;

import com.hardwarehub.product_service.enums.Category;
import com.hardwarehub.product_service.enums.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequestDTO(

        @NotNull(message = "Status is required.")
        ProductStatus status,

        @NotNull(message = "Category is required.")
        Category category,

        @NotBlank(message = "SKU is required.")
        @Size(max = 64, message = "SKU must not exceed 64 characters.")
        String sku,

        @NotBlank(message = "Name is required.")
        @Size(max = 150, message = "Name must not exceed 150 characters.")
        String name,

        @NotBlank(message = "Description is required.")
        @Size(max = 300, message = "Description must not exceed 300 characters.")
        String description,

        @NotNull(message = "Price is required.")
        @Positive(message = "Price must be greater than zero.")
        BigDecimal price,

        @NotNull(message = "Stock is required.")
        @Positive(message = "Stock must be greater than zero.")
        Integer stock
) {
}
