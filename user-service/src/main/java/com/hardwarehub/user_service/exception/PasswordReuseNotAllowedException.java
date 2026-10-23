package com.hardwarehub.user_service.exception;

public class PasswordReuseNotAllowedException extends RuntimeException {
    public PasswordReuseNotAllowedException(String message) {
        super(message);
    }
}
