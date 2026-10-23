package com.hardwarehub.user_service.dto;

import com.hardwarehub.user_service.enums.Role;
import jakarta.validation.constraints.NotNull;

public record RoleRequestDTO(

        @NotNull(message = "Role is required.")
        Role role
) {
}
