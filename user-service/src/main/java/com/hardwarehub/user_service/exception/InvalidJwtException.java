package com.hardwarehub.user_service.exception;

import io.jsonwebtoken.JwtException;

public class InvalidJwtException extends JwtException {
    public InvalidJwtException(String message) {
        super(message);
    }
}
