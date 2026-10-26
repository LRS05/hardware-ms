package com.hardwarehub.order_service.factory;

import com.hardwarehub.order_service.client.dto.ProductResponseDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductFactory
{
    public static ProductResponseDTO graphicCardDTO()
    {
        return graphicCardDTO(10);
    }

    public static ProductResponseDTO graphicCardDTO(int stock)
    {
        return graphicCardDTO(stock, 1000);
    }

    public static ProductResponseDTO graphicCardDTO(int stock, int price)
    {
        return new ProductResponseDTO(
                1,
                "ACTIVE",
                "GRAPHIC_CARD",
                "GV-N108T GAMING-OC-11G",
                "NVIDIA GTX 1080-TI",
                "This product NVIDIA GTX 1080 TI is incredible",
                BigDecimal.valueOf(price),
                stock,
                LocalDateTime.of(2026, 10, 23, 10, 5),
                LocalDateTime.of(2026, 10, 23, 10, 5)
        );
    }

    public static ProductResponseDTO processorDTO()
    {
        return new ProductResponseDTO(
                2,
                "ACTIVE",
                "PROCESSOR",
                "77490",
                "Intel Core i3-4170",
                "This product Intel Core i3-4170 is incredible",
                BigDecimal.valueOf(200),
                20,
                LocalDateTime.of(2026, 10, 23, 10, 5),
                LocalDateTime.of(2026, 10, 23, 10, 5)
        );
    }
}
