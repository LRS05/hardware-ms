package com.hardwarehub.payment_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoRequestDTO(

        String type,

        @JsonProperty("processing_mode")
        String processingMode,

        @JsonProperty("total_amount")
        String totalAmount,

        @JsonProperty("external_reference")
        String externalReference,

        String description

) {
}
