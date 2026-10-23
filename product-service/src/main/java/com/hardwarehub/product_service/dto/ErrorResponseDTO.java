package com.hardwarehub.product_service.dto;

import com.hardwarehub.product_service.enums.ApiError;

import java.time.LocalDateTime;

public record ErrorResponseDTO(

        LocalDateTime timestamp,
        ApiError error,
        int status,
        String path,
        String message
) {
}
