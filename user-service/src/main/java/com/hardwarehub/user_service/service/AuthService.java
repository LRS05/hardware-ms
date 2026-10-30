package com.hardwarehub.user_service.service;

import com.hardwarehub.user_service.dto.AuthRequestDTO;
import com.hardwarehub.user_service.dto.RegisterRequestDTO;
import com.hardwarehub.user_service.entity.UserEntity;
import com.hardwarehub.user_service.enums.Role;
import com.hardwarehub.user_service.exception.EmailAlreadyExistsException;
import com.hardwarehub.user_service.exception.InvalidJwtException;
import com.hardwarehub.user_service.exception.UsernameAlreadyExistsException;
import com.hardwarehub.user_service.repository.UserRepository;
import com.hardwarehub.user_service.enums.TokenType;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService
{
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public Map<String, ResponseCookie> register(RegisterRequestDTO requestDTO)
    {
        if (userRepository.existsByUsername(requestDTO.username()))
        {
            throw new UsernameAlreadyExistsException("Username already exists.");
        }

        if (userRepository.existsByEmail(requestDTO.email()))
        {
            throw new EmailAlreadyExistsException("Email already exists.");
        }

        UserEntity user = userRepository.save(
                UserEntity.builder()
                        .role(Role.CUSTOMER)
                        .email(requestDTO.email())
                        .username(requestDTO.username())
                        .password(passwordEncoder.encode(requestDTO.password()))
                        .firstName(requestDTO.firstName())
                        .lastName(requestDTO.lastName())
                        .creationDate(LocalDateTime.now())
                        .build()
        );

        return jwtCookies(user);
    }

    public Map<String, ResponseCookie> login(AuthRequestDTO requestDTO)
    {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                requestDTO.username(),
                requestDTO.password()
        ));

        UserEntity user = findUserByUsernameOrThrow(requestDTO.username());

        return jwtCookies(user);
    }

    public ResponseCookie refresh(Cookie[] cookies)
    {
        String refreshToken = jwtService.getTokenFromCookies(TokenType.REFRESH, cookies);

        UserEntity user = findUserByIdOrThrow(jwtService.getUserId(refreshToken));

        if (!jwtService.getType(refreshToken).equals("REFRESH") || !jwtService.isValid(refreshToken, user))
        {
            throw new InvalidJwtException("Invalid or expired refresh token.");
        }

        return jwtService.generateAccessTokenCookie(user);
    }

    public Map<String, ResponseCookie> logout()
    {
        return Map.of(
                "access-token", jwtService.emptyCookie("access-token"),
                "refresh-token", jwtService.emptyCookie("refresh-token")
        );
    }

    private Map<String, ResponseCookie> jwtCookies(UserEntity user)
    {
        return Map.of(
                "access-token", jwtService.generateAccessTokenCookie(user),
                "refresh-token", jwtService.generateRefreshTokenCookie(user)
        );
    }

    private UserEntity findUserByIdOrThrow(int userId)
    {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));
    }

    private UserEntity findUserByUsernameOrThrow(String username)
    {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found."));
    }
}
