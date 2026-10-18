package com.hardwarehub.user_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserUpdateRequestDTO(

        String email,

        String username,

        @JsonProperty("first_name")
        String firstName,

        @JsonProperty("last_name")
        String lastName
) {
}
