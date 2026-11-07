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
@ConditionalOnProperty(prefix = "demo.data", name = "enabled", havingValue = "true")
public class DemoDataInitializer implements CommandLineRunner
{
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${demo.customer.username}")
    private String username;

    @Value("${demo.customer.email}")
    private String email;

    @Value("${demo.customer.password}")
    private String password;

    @Override
    public void run(String... args)
    {
        if (username.isBlank() || email.isBlank() || password.isBlank())
        {
            throw new IllegalStateException("CUSTOMER username, email and password are required.");
        }

        if (userRepository.existsByUsername(username)
                || userRepository.existsByEmail(email)
        )
        {
            return;
        }

        userRepository.save(UserEntity.builder()
                .role(Role.CUSTOMER)
                .email(email)
                .username(username)
                .password(passwordEncoder.encode(password))
                .firstName("John")
                .lastName("Doe")
                .creationDate(LocalDateTime.now())
                .build()
        );
    }
}
