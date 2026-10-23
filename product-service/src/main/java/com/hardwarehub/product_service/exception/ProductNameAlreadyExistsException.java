package com.hardwarehub.product_service.exception;

public class ProductNameAlreadyExistsException extends RuntimeException {
    public ProductNameAlreadyExistsException(String message) {
        super(message);
    }
}
