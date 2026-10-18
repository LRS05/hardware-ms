package com.hardwarehub.order_service.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserResponseDTO(

        long id,

        String username,

        String email,

        @JsonProperty("first_name")
        String firstName,

        @JsonProperty("last_name")
        String lastName
) {
}
