package com.hardwarehub.order_service.exception;

public class PendingOrderExistsException extends RuntimeException {
    public PendingOrderExistsException(String message) {
        super(message);
    }
}
