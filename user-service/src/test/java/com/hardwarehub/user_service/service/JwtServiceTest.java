package com.hardwarehub.user_service.service;

import com.hardwarehub.user_service.entity.UserEntity;
import com.hardwarehub.user_service.enums.TokenType;
import com.hardwarehub.user_service.exception.CookieNotFoundException;
import com.hardwarehub.user_service.exception.InvalidJwtException;
import com.hardwarehub.user_service.factory.UserFactory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class JwtServiceTest
{
    @InjectMocks
    private JwtService jwtService;

    @BeforeEach
    void setUp()
    {
        ReflectionTestUtils.setField(jwtService, "secretKey", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(jwtService, "accessExpiration", 900000L);
        ReflectionTestUtils.setField(jwtService, "refreshExpiration", 86400000L);
    }

    @Test
    void generateAccessTokenCookie_whenUserIsProvided_thenReturnAccessCookieWithSignedToken()
    {
        // Given
        UserEntity user = UserFactory.customer();

        SecretKey secretKey = Keys.hmacShaKeyFor(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
        );

        // When
        ResponseCookie result = jwtService.generateAccessTokenCookie(user);

        // Then
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(result.getValue())
                .getPayload();

        assertEquals("access-token", result.getName());
        assertEquals(Duration.ofMinutes(15), result.getMaxAge());
        assertTrue(result.isHttpOnly());
        assertTrue(result.isSecure());
        assertEquals("Strict", result.getSameSite());
        assertEquals("/", result.getPath());

        assertEquals(user.getUsername(), claims.getSubject());
        assertEquals(user.getId(), claims.get("userId", Integer.class));
        assertEquals(user.getRole().name(), claims.get("role", String.class));
        assertEquals("ACCESS", claims.get("type", String.class));
        assertNotNull(claims.getId());
        assertNotNull(claims.getIssuedAt());
        assertEquals(900000L, claims.getExpiration().getTime() - claims.getIssuedAt().getTime(), 1000.0);
    }

    @Test
    void generateRefreshTokenCookie_whenUserIsProvided_thenReturnRefreshCookieWithSignedToken()
    {
        // Given
        UserEntity user = UserFactory.customer();

        SecretKey secretKey = Keys.hmacShaKeyFor(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
        );

        // When
        ResponseCookie result = jwtService.generateRefreshTokenCookie(user);

        // Then
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(result.getValue())
                .getPayload();

        assertEquals("refresh-token", result.getName());
        assertEquals(Duration.ofMinutes(1440), result.getMaxAge());
        assertTrue(result.isHttpOnly());
        assertTrue(result.isSecure());
        assertEquals("Strict", result.getSameSite());
        assertEquals("/", result.getPath());

        assertEquals(user.getUsername(), claims.getSubject());
        assertEquals(user.getId(), claims.get("userId", Integer.class));
        assertEquals(user.getRole().name(), claims.get("role", String.class));
        assertEquals("REFRESH", claims.get("type", String.class));
        assertNotNull(claims.getId());
        assertNotNull(claims.getIssuedAt());
        assertEquals(86400000L, claims.getExpiration().getTime() - claims.getIssuedAt().getTime(), 1000.0);
    }

    @Test
    void isValid_whenTokenBelongsToUser_thenReturnTrue()
    {
        // Given
        UserEntity user = UserFactory.customer();
        ResponseCookie accessTokenCookie = jwtService.generateAccessTokenCookie(user);

        // When
        boolean result = jwtService.isValid(accessTokenCookie.getValue(), user);

        // Then
        assertTrue(result);
    }

    @Test
    void isValid_whenTokenBelongsToAnotherUser_thenReturnFalse()
    {
        // Given
        UserEntity user = UserFactory.customer();
        UserEntity admin = UserFactory.admin();
        ResponseCookie accessTokenCookie = jwtService.generateAccessTokenCookie(user);

        // When
        boolean result = jwtService.isValid(accessTokenCookie.getValue(), admin);

        // Then
        assertFalse(result);
    }

    @Test
    void isValid_whenTokenIsInvalid_thenReturnFalse()
    {
        // Given
        UserEntity user = UserFactory.customer();
        String token = "invalid-token";

        // When
        boolean result = jwtService.isValid(token, user);

        // Then
        assertFalse(result);
    }

    @Test
    void isValid_whenTokenIsExpired_thenReturnFalse()
    {
        // Given
        UserEntity user = UserFactory.customer();
        ReflectionTestUtils.setField(jwtService, "accessExpiration", -60000L);

        ResponseCookie accessTokenCookie = jwtService.generateAccessTokenCookie(user);

        // When
        boolean result = jwtService.isValid(accessTokenCookie.getValue(), user);

        // Then
        assertFalse(result);
    }

    @Test
    void getTokenFromCookies_whenAccessCookieExists_thenReturnAccessToken()
    {
        // Given
        Cookie[] cookies = {
                new Cookie("access-token", "access-token-value"),
                new Cookie("refresh-token", "refresh-token-value")
        };

        // When
        String result = jwtService.getTokenFromCookies(TokenType.ACCESS, cookies);

        // Then
        assertEquals("access-token-value", result);
    }

    @Test
    void getTokenFromCookies_whenRefreshCookieExists_thenReturnRefreshToken()
    {
        // Given
        Cookie[] cookies = {
                new Cookie("access-token", "access-token-value"),
                new Cookie("refresh-token", "refresh-token-value")
        };

        // When
        String result = jwtService.getTokenFromCookies(TokenType.REFRESH, cookies);

        // Then
        assertEquals("refresh-token-value", result);
    }

    @Test
    void getTokenFromCookies_whenCookiesAreNull_thenThrowCookieNotFoundException()
    {
        // Given
        Cookie[] cookies = null;

        // When / Then
        assertThrows(CookieNotFoundException.class, () -> jwtService.getTokenFromCookies(TokenType.REFRESH, cookies));
    }

    @Test
    void getTokenFromCookies_whenRequestedCookieIsMissing_thenThrowCookieNotFoundException()
    {
        // Given
        Cookie[] cookies = {new Cookie("access-token", "access-token-value")};

        // When / Then
        assertThrows(CookieNotFoundException.class, () -> jwtService.getTokenFromCookies(TokenType.REFRESH, cookies));
    }

    @Test
    void getSubject_whenTokenIsValid_thenReturnUsername()
    {
        // Given
        UserEntity user = UserFactory.customer();
        ResponseCookie accessTokenCookie = jwtService.generateAccessTokenCookie(user);

        // When
        String result = jwtService.getSubject(accessTokenCookie.getValue());

        // Then
        assertEquals(user.getUsername(), result);
    }

    @Test
    void getSubject_whenTokenIsInvalid_thenThrowInvalidJwtException()
    {
        // Given
        String token = "invalid-token";

        // When / Then
        assertThrows(InvalidJwtException.class, () -> jwtService.getSubject(token));
    }

    @Test
    void getType_whenTokenIsAccess_thenReturnAccessType()
    {
        // Given
        UserEntity user = UserFactory.customer();
        ResponseCookie accessTokenCookie = jwtService.generateAccessTokenCookie(user);

        // When
        String result = jwtService.getType(accessTokenCookie.getValue());

        // Then
        assertEquals("ACCESS", result);
    }

    @Test
    void getType_whenTokenIsRefresh_thenReturnRefreshType()
    {
        // Given
        UserEntity user = UserFactory.customer();
        ResponseCookie refreshTokenCookie = jwtService.generateRefreshTokenCookie(user);

        // When
        String result = jwtService.getType(refreshTokenCookie.getValue());

        // Then
        assertEquals("REFRESH", result);
    }

    @Test
    void getType_whenTokenIsInvalid_thenThrowInvalidJwtException()
    {
        // Given
        String token = "invalid-token";

        // When / Then
        assertThrows(InvalidJwtException.class, () -> jwtService.getType(token));
    }

    @Test
    void emptyCookie_whenNameIsProvided_thenReturnEmptyCookieWithZeroMaxAge()
    {
        // Given
        String name = "access-token";

        // When
        ResponseCookie result = jwtService.emptyCookie(name);

        // Then
        assertEquals(name, result.getName());
        assertEquals("", result.getValue());
        assertEquals(Duration.ZERO, result.getMaxAge());
        assertTrue(result.isHttpOnly());
        assertTrue(result.isSecure());
        assertEquals("Strict", result.getSameSite());
        assertEquals("/", result.getPath());
    }
}
