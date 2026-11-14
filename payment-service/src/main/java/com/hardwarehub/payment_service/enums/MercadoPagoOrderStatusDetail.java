package com.hardwarehub.payment_service.enums;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum MercadoPagoOrderStatusDetail
{
    @JsonProperty("created")
    CREATED,

    @JsonProperty("accredited")
    ACCREDITED,

    @JsonProperty("partially_refunded")
    PARTIALLY_REFUNDED,

    @JsonProperty("waiting_capture")
    WAITING_CAPTURE,

    @JsonProperty("bad_filled_card_data")
    BAD_FILLED_CARD_DATA,

    @JsonProperty("invalid_card_token")
    INVALID_CARD_TOKEN,

    @JsonProperty("high_risk")
    HIGH_RISK,

    @JsonProperty("rejected_by_issuer")
    REJECTED_BY_ISSUER,

    @JsonProperty("required_call_for_authorize")
    REQUIRED_CALL_FOR_AUTHORIZE,

    @JsonProperty("max_attempts_exceeded")
    MAX_ATTEMPTS_EXCEEDED,

    @JsonProperty("card_disabled")
    CARD_DISABLED,

    @JsonProperty("insufficient_amount")
    INSUFFICIENT_AMOUNT,

    @JsonProperty("amount_limit_exceeded")
    AMOUNT_LIMIT_EXCEEDED,

    @JsonProperty("invalid_installments")
    INVALID_INSTALLMENTS,

    @JsonProperty("processing_error")
    PROCESSING_ERROR,

    @JsonProperty("pending_review_manual")
    PENDING_REVIEW_MANUAL,

    @JsonProperty("in_process")
    IN_PROCESS,

    @JsonProperty("refunded")
    REFUNDED,

    @JsonProperty("canceled")
    CANCELED,

    @JsonEnumDefaultValue
    UNKNOWN
}
