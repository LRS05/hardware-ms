package com.hardwarehub.user_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hardwarehub.user_service.enums.Role;

import java.time.LocalDateTime;

public record UserResponseDTO(

        int id,

        Role role,

        String username,

        String email,

        @JsonProperty("first_name")
        String firstName,

        @JsonProperty("last_name")
        String lastName,

        @JsonProperty("creation_date")
        LocalDateTime creationDate
) {
}
