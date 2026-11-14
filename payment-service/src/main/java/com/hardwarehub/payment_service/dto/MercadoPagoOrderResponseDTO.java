package com.hardwarehub.payment_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.hardwarehub.payment_service.enums.MercadoPagoOrderStatus;
import com.hardwarehub.payment_service.enums.MercadoPagoOrderStatusDetail;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoOrderResponseDTO(

        String id,

        @JsonProperty("checkout_url")
        String checkoutUrl,

        @JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
        MercadoPagoOrderStatus status,

        @JsonProperty("status_detail")
        @JsonFormat(with = JsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
        MercadoPagoOrderStatusDetail statusDetail,

        @JsonProperty("external_reference")
        String externalReference,

        @JsonProperty("total_amount")
        BigDecimal totalAmount,

        @JsonProperty("total_paid_amount")
        BigDecimal totalPaidAmount,

        String currency
) {
}
