package com.hardwarehub.user_service.service;

import com.hardwarehub.user_service.entity.UserEntity;
import com.hardwarehub.user_service.enums.TokenType;
import com.hardwarehub.user_service.exception.CookieNotFoundException;
import com.hardwarehub.user_service.exception.InvalidJwtException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService
{
    @Value("${jwt.secret-key}")
    private String secretKey;

    @Value("${jwt.access.expiration}")
    private long accessExpiration;

    @Value("${jwt.refresh.expiration}")
    private long refreshExpiration;

    public ResponseCookie generateAccessTokenCookie(UserEntity user)
    {
        String accessToken = buildToken(user, TokenType.ACCESS, accessExpiration);
        return buildCookie("access-token", accessToken, 15);
    }

    public ResponseCookie generateRefreshTokenCookie(UserEntity user)
    {
        String refreshToken = buildToken(user, TokenType.REFRESH, refreshExpiration);
        return buildCookie("refresh-token", refreshToken, 1440);
    }

    public boolean isValid(String token, UserEntity user)
    {
        try
        {
            int userId = getUserId(token);
            return user.getId() == userId && parseToken(token).getExpiration().after(new Date());
        }
        catch (JwtException e)
        {
            return false;
        }
    }

    public String getTokenFromCookies(TokenType tokenType, Cookie[] cookies)
    {
        if (cookies == null) throw new CookieNotFoundException("Cookies are empty.");

        String cookieName = tokenType.name().toLowerCase() + "-token";

        return Arrays.stream(cookies)
                .filter(c -> c.getName().equals(cookieName))
                .findFirst()
                .map(Cookie::getValue)
                .orElseThrow(() -> new CookieNotFoundException(cookieName + " cookie not found."));
    }

    public String getSubject(String token)
    {
        return parseToken(token).getSubject();
    }

    public int getUserId(String token)
    {
        try
        {
            int userId = Integer.parseInt(getSubject(token));

            if (userId <= 0)
            {
                throw new InvalidJwtException("Invalid JWT subject.");
            }

            return userId;
        }
        catch (NumberFormatException e)
        {
            throw new InvalidJwtException("Invalid JWT subject.");
        }
    }

    public String getType(String token)
    {
        return parseToken(token).get("type", String.class);
    }

    public ResponseCookie emptyCookie(String name)
    {
        return buildCookie(name, "", 0);
    }

    private Claims parseToken(String token)
    {
        try
        {
            return Jwts.parser()
                    .verifyWith(secretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        }
        catch (JwtException e)
        {
            throw new InvalidJwtException("Invalid or expired JWT.");
        }
    }

    private ResponseCookie buildCookie(String name, String value, int durationOfMinutes)
    {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofMinutes(durationOfMinutes))
                .build();
    }

    private String buildToken(UserEntity user, TokenType type, long expiration)
    {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(user.getId()))
                .claims(Map.of(
                        "username", user.getUsername(),
                        "role", user.getRole(),
                        "type", type
                ))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(secretKey())
                .compact();
    }

    private SecretKey secretKey()
    {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
}
