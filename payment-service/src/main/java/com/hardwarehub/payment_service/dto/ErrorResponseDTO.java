package com.hardwarehub.payment_service.dto;

import com.hardwarehub.payment_service.enums.ApiError;

import java.time.LocalDateTime;

public record ErrorResponseDTO(

        LocalDateTime timestamp,
        ApiError error,
        int status,
        String path,
        String message
) {
}
