package com.hardwarehub.user_service.exception;

public class CookieNotFoundException extends RuntimeException {
    public CookieNotFoundException(String message) {
        super(message);
    }
}
