package com.hardwarehub.user_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterRequestDTO(

        @NotBlank(message = "Email is required.")
        @Email(message = "Invalid email format.")
        String email,

        @NotBlank(message = "Username is required.")
        @Pattern(
                regexp = "^[a-zA-Z0-9]{3,20}$",
                message = "Username must be between 3 and 20 characters and contain only letters and numbers."
        )
        String username,

        @NotBlank(message = "Password is required.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&._-])[A-Za-z\\d@$!%*?&._-]{8,}$",
                message = "The password must be at least 8 characters long and include at least one uppercase letter, one lowercase letter, one number, and one special character."
        )
        String password,

        @NotBlank(message = "First name is required.")
        @Pattern(regexp = "^[\\p{L}]+(?:[ '-][\\p{L}]+)*$", message = "First name can only contain letters.")
        @JsonProperty("first_name")
        String firstName,

        @NotBlank(message = "Last name is required.")
        @Pattern(regexp = "^[\\p{L}]+(?:[ '-][\\p{L}]+)*$", message = "Last name can only contain letters.")
        @JsonProperty("last_name")
        String lastName
) {
}
