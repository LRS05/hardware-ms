package com.hardwarehub.user_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PasswordUpdateRequestDTO(

        @JsonProperty("current_password")
        String currentPassword,

        @JsonProperty("new_password")
        String newPassword
) {
}
