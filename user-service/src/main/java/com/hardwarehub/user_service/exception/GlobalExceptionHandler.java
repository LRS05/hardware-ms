package com.hardwarehub.user_service.exception;

import com.hardwarehub.user_service.dto.ErrorResponseDTO;
import com.hardwarehub.user_service.enums.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler
{
    private final HttpServletRequest httpServletRequest;

    @ExceptionHandler(CookieNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleCookieNotFoundException(CookieNotFoundException e)
    {
        return sendErrorResponse(HttpStatus.NOT_FOUND, ApiError.COOKIE_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDTO> handleEmailAlreadyExistsException(EmailAlreadyExistsException e)
    {
        return sendErrorResponse(HttpStatus.CONFLICT, ApiError.EMAIL_ALREADY_EXISTS, e.getMessage());
    }

    @ExceptionHandler(InvalidCurrentPasswordException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidCurrentPasswordException(InvalidCurrentPasswordException e)
    {
        return sendErrorResponse(HttpStatus.BAD_REQUEST, ApiError.INVALID_CURRENT_PASSWORD, e.getMessage());
    }

    @ExceptionHandler(InvalidJwtException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidJwtException(InvalidJwtException e)
    {
        return sendErrorResponse(HttpStatus.UNAUTHORIZED, ApiError.INVALID_JWT, e.getMessage());
    }

    @ExceptionHandler(PasswordReuseNotAllowedException.class)
    public ResponseEntity<ErrorResponseDTO> handlePasswordReuseNotAllowedException(PasswordReuseNotAllowedException e)
    {
        return sendErrorResponse(HttpStatus.BAD_REQUEST, ApiError.PASSWORD_REUSE_NOT_ALLOWED, e.getMessage());
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDTO> handleUsernameAlreadyExistsException(UsernameAlreadyExistsException e)
    {
        return sendErrorResponse(HttpStatus.CONFLICT, ApiError.USERNAME_ALREADY_EXISTS, e.getMessage());
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleUsernameNotFoundException(UsernameNotFoundException e)
    {
        return sendErrorResponse(HttpStatus.NOT_FOUND, ApiError.USERNAME_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDeniedException(AccessDeniedException e)
    {
        return sendErrorResponse(HttpStatus.FORBIDDEN, ApiError.ACCESS_DENIED, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e)
    {
        Map<String, String> fieldMessages = new HashMap<>();

        e.getFieldErrors().forEach(error -> fieldMessages.put(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(fieldMessages);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e)
    {
        return sendErrorResponse(HttpStatus.BAD_REQUEST, ApiError.METHOD_ARGUMENT_TYPE_MISMATCH, "Invalid parameter type.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleHttpMessageNotReadableException(HttpMessageNotReadableException e)
    {
        return sendErrorResponse(HttpStatus.BAD_REQUEST, ApiError.HTTP_MESSAGE_NOT_READABLE, "Invalid request body.");
    }

    private ResponseEntity<ErrorResponseDTO> sendErrorResponse(HttpStatus status, ApiError error, String message)
    {
        return ResponseEntity.status(status)
                .body(new ErrorResponseDTO(
                        LocalDateTime.now(),
                        error,
                        status.value(),
                        httpServletRequest.getRequestURI(),
                        message
                ));
    }
}
