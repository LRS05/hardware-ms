package com.hardwarehub.order_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hardwarehub.order_service.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

public record OrderResponseDTO(

        int id,

        OrderStatus status,

        @JsonProperty("total_price")
        BigDecimal totalPrice,

        @JsonProperty("order_items")
        List<OrderItemResponseDTO> orderItems
) {
}
