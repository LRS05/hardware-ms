package com.hardwarehub.product_service.exception;

import com.hardwarehub.product_service.dto.ErrorResponseDTO;
import com.hardwarehub.product_service.enums.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
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
    private final HttpServletRequest request;

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponseDTO> handleInsufficientStockException(InsufficientStockException e)
    {
        return sendErrorResponse(HttpStatus.CONFLICT, ApiError.INSUFFICIENT_STOCK, e.getMessage());
    }

    @ExceptionHandler(ProductNameAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDTO> handleProductNameAlreadyExistsException(ProductNameAlreadyExistsException e)
    {
        return sendErrorResponse(HttpStatus.CONFLICT, ApiError.PRODUCT_NAME_ALREADY_EXISTS, e.getMessage());
    }

    @ExceptionHandler(ProductSkuAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDTO> handleProductSkuAlreadyExistsException(ProductSkuAlreadyExistsException e)
    {
        return sendErrorResponse(HttpStatus.CONFLICT, ApiError.PRODUCT_SKU_ALREADY_EXISTS, e.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleProductNotFoundException(ProductNotFoundException e)
    {
        return sendErrorResponse(HttpStatus.NOT_FOUND, ApiError.PRODUCT_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ProductOutOfStockException.class)
    public ResponseEntity<ErrorResponseDTO> handleProductOutOfStockException(ProductOutOfStockException e)
    {
        return sendErrorResponse(HttpStatus.CONFLICT, ApiError.PRODUCT_OUT_OF_STOCK, e.getMessage());
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

    private ResponseEntity<ErrorResponseDTO> sendErrorResponse(HttpStatus status, ApiError error, String message)
    {
        return ResponseEntity.status(status)
                .body(new ErrorResponseDTO(
                        LocalDateTime.now(),
                        error,
                        status.value(),
                        request.getRequestURI(),
                        message
                ));
    }
}
