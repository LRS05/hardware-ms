package com.hardwarehub.user_service.factory;

import com.hardwarehub.user_service.dto.UserResponseDTO;
import com.hardwarehub.user_service.entity.UserEntity;
import com.hardwarehub.user_service.enums.Role;

import java.time.LocalDateTime;

public class UserFactory
{
    public static UserEntity admin()
    {
        return buildUser(1, Role.ADMIN, "lorenzo@gmail.com", "Lorenzosarlo", "Lorenzo", "Sarlo");
    }

    public static UserEntity customer()
    {
        return buildUser(2, Role.CUSTOMER, "marcelo@gmail.com", "Marceloviviani", "Marcelo", "Viviani");
    }

    public static UserEntity customerRoleUpdated()
    {
        return buildUser(2, Role.ADMIN, "marcelo@gmail.com", "Marceloviviani", "Marcelo", "Viviani");
    }

    public static UserEntity adminUpdated()
    {
        return buildUser(1, Role.ADMIN, "eduardo@gmail.com", "Eduviviani", "Eduardo", "Viviani");
    }

    public static UserEntity customerUpdated()
    {
        return buildUser(2, Role.CUSTOMER, "alfredo@gmail.com", "Alfredoviviani", "Alfredo", "Viviani");
    }

    public static UserResponseDTO customerRoleUpdatedDTO()
    {
        return entityToDTO(customerRoleUpdated());
    }

    public static UserResponseDTO adminDTO()
    {
        return entityToDTO(admin());
    }

    public static UserResponseDTO customerDTO()
    {
        return entityToDTO(customer());
    }

    public static UserResponseDTO adminUpdatedDTO()
    {
        return entityToDTO(adminUpdated());
    }

    public static UserResponseDTO customerUpdatedDTO()
    {
        return entityToDTO(customerUpdated());
    }

    private static UserResponseDTO entityToDTO(UserEntity user)
    {
        return new UserResponseDTO(
                user.getId(),
                user.getRole(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getCreationDate()
        );
    }

    private static UserEntity buildUser(int id, Role role, String email, String username, String firstName, String lastName)
    {
        return UserEntity.builder()
                .id(id)
                .role(role)
                .email(email)
                .username(username)
                .password("$2a$10$ZwW9siqoGWhytDWWRbpWIuxI4u75CyNfTmf9p4go5vvjULBXtJF1K") // Password -> Gordomono8!
                .firstName(firstName)
                .lastName(lastName)
                .creationDate(LocalDateTime.of(2026, 10, 22, 10, 5))
                .build();
    }
}
