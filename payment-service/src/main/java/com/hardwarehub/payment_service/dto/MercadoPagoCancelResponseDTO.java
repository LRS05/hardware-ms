package com.hardwarehub.payment_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.hardwarehub.payment_service.enums.MercadoPagoOrderStatus;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoCancelResponseDTO(

        String id,

        @JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
        MercadoPagoOrderStatus status
) {
}
