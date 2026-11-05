package com.hardwarehub.user_service.config;

import com.hardwarehub.user_service.entity.UserEntity;
import com.hardwarehub.user_service.enums.Role;
import com.hardwarehub.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dev.admin", name = "enabled", havingValue = "true")
public class AdminInitializer implements CommandLineRunner
{
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${dev.admin.username}")
    private String username;

    @Value("${dev.admin.email}")
    private String email;

    @Value("${dev.admin.password}")
    private String password;

    @Override
    public void run(String... args)
    {
        if (username.isBlank() || email.isBlank() || password.isBlank())
        {
            throw new IllegalStateException("ADMIN username, email and password are required.");
        }

        if (userRepository.existsByUsername(username)
                || userRepository.existsByEmail(email)
                || userRepository.existsByRole(Role.ADMIN))
        {
            return;
        }

        userRepository.save(UserEntity.builder()
                .role(Role.ADMIN)
                .email(email)
                .username(username)
                .password(passwordEncoder.encode(password))
                .firstName("ADMIN")
                .lastName("ADMIN")
                .creationDate(LocalDateTime.now())
                .build()
        );
    }
}
