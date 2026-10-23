package com.hardwarehub.user_service.exception;

public class InvalidCurrentPasswordException extends RuntimeException {
    public InvalidCurrentPasswordException(String message) {
        super(message);
    }
}
