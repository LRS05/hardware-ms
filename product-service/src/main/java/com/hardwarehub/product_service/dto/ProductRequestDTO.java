package com.hardwarehub.product_service.dto;

import com.hardwarehub.product_service.entity.enums.Category;
import com.hardwarehub.product_service.entity.enums.ProductStatus;

import java.math.BigDecimal;

public record ProductRequestDTO(

        ProductStatus status,

        Category category,

        String sku,

        String name,

        String description,

        BigDecimal price,

        Integer stock
) {
}
