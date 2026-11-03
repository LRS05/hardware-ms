package com.hardwarehub.order_service.exception;

public class ProductTimeoutException extends RuntimeException {
    public ProductTimeoutException(String message) {
        super(message);
    }
}
