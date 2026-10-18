package com.hardwarehub.user_service.controller;

import com.hardwarehub.user_service.dto.PasswordUpdateRequestDTO;
import com.hardwarehub.user_service.dto.RoleRequestDTO;
import com.hardwarehub.user_service.dto.UserResponseDTO;
import com.hardwarehub.user_service.dto.UserUpdateRequestDTO;
import com.hardwarehub.user_service.enums.Role;
import com.hardwarehub.user_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminController
{
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getMe()
    {
        return ResponseEntity.ok(userService.getMe());
    }

    @GetMapping("/users")
    public ResponseEntity<Page<UserResponseDTO>> getUsers(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Role role,
            Pageable pageable)
    {
        // TODO: Create userService.search() for using multiple request parameters.
        if (username != null)
        {
            return ResponseEntity.ok(userService.getAllByUsernameLike(username, pageable));
        }

        if (role != null)
        {
            return ResponseEntity.ok(userService.getAllByRole(role, pageable));
        }

        return ResponseEntity.ok(userService.getAll(pageable));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable int id)
    {
        return ResponseEntity.ok(userService.getById(id));
    }

    @GetMapping("/users/email/{email}")
    public ResponseEntity<UserResponseDTO> getUserByEmail(@PathVariable String email)
    {
        return ResponseEntity.ok(userService.getByEmail(email));
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponseDTO> updateMe(@Valid @RequestBody UserUpdateRequestDTO requestDTO)
    {
        return ResponseEntity.ok(userService.updateMe(requestDTO));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/users/{id}")
    public ResponseEntity<UserResponseDTO> updateUserById(@PathVariable int id, @Valid @RequestBody UserUpdateRequestDTO requestDTO)
    {
        return ResponseEntity.ok(userService.updateById(id, requestDTO));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/users/{id}/role")
    public ResponseEntity<UserResponseDTO> updateUserRoleById(@PathVariable int id, @Valid @RequestBody RoleRequestDTO requestDTO)
    {
        return ResponseEntity.ok(userService.updateRoleById(id, requestDTO));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<String> changePassword(@Valid @RequestBody PasswordUpdateRequestDTO requestDTO)
    {
        userService.changePassword(requestDTO);
        return ResponseEntity.ok("Your password has been successfully changed.");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUserById(@PathVariable int id)
    {
        userService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
