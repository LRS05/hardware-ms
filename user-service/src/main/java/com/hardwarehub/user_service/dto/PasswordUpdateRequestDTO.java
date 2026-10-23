package com.hardwarehub.user_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordUpdateRequestDTO(

        @NotBlank(message = "Password is required.")
        @JsonProperty("current_password")
        String currentPassword,

        @NotBlank(message = "Password is required.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&._-])[A-Za-z\\d@$!%*?&._-]{8,}$",
                message = "The password must be at least 8 characters long and include at least one uppercase letter, one lowercase letter, one number, and one special character."
        )
        @JsonProperty("new_password")
        String newPassword
) {
}
