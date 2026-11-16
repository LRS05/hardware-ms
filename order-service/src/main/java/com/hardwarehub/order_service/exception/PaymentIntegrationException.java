package com.hardwarehub.order_service.exception;

public class PaymentIntegrationException extends RuntimeException {
    public PaymentIntegrationException(String message) {
        super(message);
    }
}
