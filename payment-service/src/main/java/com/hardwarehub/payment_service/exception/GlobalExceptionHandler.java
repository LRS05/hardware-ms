package com.hardwarehub.payment_service.exception;

import com.hardwarehub.payment_service.dto.ErrorResponseDTO;
import com.hardwarehub.payment_service.enums.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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

    @ExceptionHandler(UnsupportedPaymentProviderException.class)
    public ResponseEntity<ErrorResponseDTO> handleUnsupportedPaymentProviderException(UnsupportedPaymentProviderException e)
    {
        return sendErrorResponse(HttpStatus.BAD_REQUEST, ApiError.UNSUPPORTED_PAYMENT_PROVIDER, e.getMessage());
    }

    @ExceptionHandler(InvalidPaymentRequestException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidPaymentRequestException(InvalidPaymentRequestException e)
    {
        return sendErrorResponse(HttpStatus.BAD_REQUEST, ApiError.INVALID_PAYMENT_REQUEST, e.getMessage());
    }

    @ExceptionHandler(PaymentAlreadyCompletedException.class)
    public ResponseEntity<ErrorResponseDTO> handlePaymentAlreadyCompletedException(PaymentAlreadyCompletedException e)
    {
        return sendErrorResponse(HttpStatus.CONFLICT, ApiError.PAYMENT_ALREADY_COMPLETED, e.getMessage());
    }

    @ExceptionHandler(InvalidPaymentStateException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidPaymentStateException(InvalidPaymentStateException e)
    {
        return sendErrorResponse(HttpStatus.CONFLICT, ApiError.INVALID_PAYMENT_STATE, e.getMessage());
    }

    @ExceptionHandler(PaymentProviderIntegrationException.class)
    public ResponseEntity<ErrorResponseDTO> handlePaymentProviderIntegrationException(PaymentProviderIntegrationException e)
    {
        return sendErrorResponse(HttpStatus.BAD_GATEWAY, ApiError.PAYMENT_PROVIDER_INTEGRATION, e.getMessage());
    }

    @ExceptionHandler(PaymentProviderDataMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handlePaymentProviderDataMismatchException(PaymentProviderDataMismatchException e)
    {
        return sendErrorResponse(HttpStatus.BAD_GATEWAY, ApiError.PAYMENT_PROVIDER_DATA_MISMATCH, e.getMessage());
    }

    @ExceptionHandler(PaymentAttemptNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handlePaymentAttemptNotFoundException(PaymentAttemptNotFoundException e)
    {
        return sendErrorResponse(HttpStatus.NOT_FOUND, ApiError.PAYMENT_ATTEMPT_NOT_FOUND, e.getMessage());
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e)
    {
        Map<String, String> fieldMessages = new HashMap<>();

        e.getFieldErrors().forEach(error -> fieldMessages.put(error.getField(), error.getDefaultMessage()));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(fieldMessages);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataIntegrityViolationException(DataIntegrityViolationException e)
    {
        return sendErrorResponse(HttpStatus.CONFLICT, ApiError.DATA_INTEGRITY_VIOLATION, "The operation conflicts with the current payment data.");
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
