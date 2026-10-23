package com.hardwarehub.product_service.exception;

public class ProductSkuAlreadyExistsException extends RuntimeException {
    public ProductSkuAlreadyExistsException(String message) {
        super(message);
    }
}
