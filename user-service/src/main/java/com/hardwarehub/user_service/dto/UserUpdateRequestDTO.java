package com.hardwarehub.user_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

public record UserUpdateRequestDTO(

        @Email(message = "Invalid email format.")
        String email,

        @Pattern(
                regexp = "^[a-zA-Z0-9]{3,20}$",
                message = "Username must be between 3 and 20 characters and contain only letters and numbers."
        )
        String username,

        @Pattern(
                regexp = "^[\\p{L}]+(?:[ '-][\\p{L}]+)*$",
                message = "First name can only contain letters."
        )
        @JsonProperty("first_name")
        String firstName,

        @Pattern(regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑ]+$", message = "Last name can only contain letters.")
        @JsonProperty("last_name")
        String lastName
) {
}
