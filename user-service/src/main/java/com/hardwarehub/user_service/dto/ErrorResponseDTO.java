package com.hardwarehub.user_service.dto;

import com.hardwarehub.user_service.enums.ApiError;

import java.time.LocalDateTime;

public record ErrorResponseDTO(

        LocalDateTime timestamp,
        ApiError error,
        int status,
        String path,
        String message
) {
}
