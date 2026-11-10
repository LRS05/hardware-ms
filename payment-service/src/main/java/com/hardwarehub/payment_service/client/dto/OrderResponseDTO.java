package com.hardwarehub.payment_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderResponseDTO(

        int id,

        String status,

        @JsonProperty("total_price")
        BigDecimal totalPrice

) {
}
