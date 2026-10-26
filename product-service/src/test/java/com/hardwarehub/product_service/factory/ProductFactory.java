package com.hardwarehub.product_service.factory;

import com.hardwarehub.product_service.dto.ProductResponseDTO;
import com.hardwarehub.product_service.entity.ProductEntity;
import com.hardwarehub.product_service.enums.Category;
import com.hardwarehub.product_service.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductFactory
{
    public static ProductEntity graphicCard()
    {
        return buildProduct(
                1,
                ProductStatus.ACTIVE,
                Category.GRAPHIC_CARD,
                "GV-N108T GAMING-OC-11G",
                "NVIDIA GTX 1080 TI",
                BigDecimal.valueOf(1000),
                50
        );
    }

    public static ProductEntity processor()
    {
        return buildProduct(
                2,
                ProductStatus.ACTIVE,
                Category.PROCESSOR,
                "77490",
                "Intel Core i3-4170",
                BigDecimal.valueOf(200),
                20
        );
    }

    public static ProductResponseDTO graphicCardDTO()
    {
        return entityToDTO(graphicCard());
    }

    public static ProductResponseDTO processorDTO()
    {
        return entityToDTO(processor());
    }

    private static ProductResponseDTO entityToDTO(ProductEntity product)
    {
        return new ProductResponseDTO(
                product.getId(),
                product.getCategory(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }

    private static ProductEntity buildProduct(
            int id,
            ProductStatus status,
            Category category,
            String sku,
            String name,
            BigDecimal price,
            Integer stock
    )
    {
        return ProductEntity.builder()
                .id(id)
                .status(status)
                .category(category)
                .sku(sku)
                .name(name)
                .description("This product " + name + " is incredible")
                .price(price)
                .stock(stock)
                .createdAt(LocalDateTime.of(2026, 10, 23, 10, 5))
                .updatedAt(LocalDateTime.of(2026, 10, 23, 10, 5))
                .build();
    }
}
