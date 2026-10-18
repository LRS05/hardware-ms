package com.hardwarehub.user_service.controller;

import com.hardwarehub.user_service.dto.PasswordRequestDTO;
import com.hardwarehub.user_service.dto.PasswordUpdateRequestDTO;
import com.hardwarehub.user_service.dto.UserResponseDTO;
import com.hardwarehub.user_service.dto.UserUpdateRequestDTO;
import com.hardwarehub.user_service.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController
{
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> getMe()
    {
        return ResponseEntity.ok(userService.getMe());
    }

    @PatchMapping("/me")
    public ResponseEntity<UserResponseDTO> updateMe(@Valid @RequestBody UserUpdateRequestDTO requestDTO)
    {
        return ResponseEntity.ok(userService.updateMe(requestDTO));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<String> changePassword(@Valid @RequestBody PasswordUpdateRequestDTO requestDTO)
    {
        userService.changePassword(requestDTO);
        return ResponseEntity.ok("Your password has been successfully changed.");
    }

    @DeleteMapping("/me")
    public ResponseEntity<String> deleteMe(@Valid @RequestBody PasswordRequestDTO requestDTO)
    {
        userService.deleteMe(requestDTO);
        return ResponseEntity.ok("Your account has been successfully deleted.");
    }
}
