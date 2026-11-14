package com.hardwarehub.payment_service.enums;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum MercadoPagoOrderStatus
{
    @JsonProperty("created")
    CREATED,

    @JsonProperty("processed")
    PROCESSED,

    @JsonProperty("action_required")
    ACTION_REQUIRED,

    @JsonProperty("failed")
    FAILED,

    @JsonProperty("processing")
    PROCESSING,

    @JsonProperty("refunded")
    REFUNDED,

    @JsonProperty("canceled")
    CANCELED,

    @JsonEnumDefaultValue
    UNKNOWN
}
