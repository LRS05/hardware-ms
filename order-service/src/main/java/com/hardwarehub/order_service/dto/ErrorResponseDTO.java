package com.hardwarehub.order_service.dto;

import com.hardwarehub.order_service.enums.ApiError;

import java.time.LocalDateTime;

public record ErrorResponseDTO(

        LocalDateTime timestamp,
        ApiError error,
        int status,
        String path,
        String message
) {
}
