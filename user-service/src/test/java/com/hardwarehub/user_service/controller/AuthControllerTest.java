package com.hardwarehub.user_service.controller;

import com.hardwarehub.user_service.config.PostgresTestConfig;
import com.hardwarehub.user_service.dto.AuthRequestDTO;
import com.hardwarehub.user_service.dto.RegisterRequestDTO;
import com.hardwarehub.user_service.entity.UserEntity;
import com.hardwarehub.user_service.enums.ApiError;
import com.hardwarehub.user_service.enums.TokenType;
import com.hardwarehub.user_service.factory.UserFactory;
import com.hardwarehub.user_service.service.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.net.HttpCookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@Sql(statements = "DELETE FROM users")
@Sql("/data.sql")
@Sql(statements = "ALTER TABLE users ALTER COLUMN id RESTART WITH 3")
@SpringBootTest(properties = "spring.config.location=classpath:/application-test.properties", webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerTest
{
    @LocalServerPort
    private int port;

    private RestTestClient restTestClient;

    @Value("${jwt.secret-key}")
    private String jwtSecret;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp()
    {
        restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/api/v1/auth")
                .build();
    }

    @Test
    void register_whenDataIsValid_thenCreateCustomerAndReturnCookies()
    {
        RegisterRequestDTO requestDTO = new RegisterRequestDTO(
                "alfredo@gmail.com",
                "alfredoviviani",
                "Gordomono8!",
                "Alfredo",
                "Viviani"
        );

        restTestClient.post()
                .uri("/register")
                .body(requestDTO)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valuesMatch(
                        HttpHeaders.SET_COOKIE,
                        "access-token=[^;]+;.*",
                        "refresh-token=[^;]+;.*"
                )
                .expectBody().isEmpty();
    }

    @Test
    void register_whenUsernameIsAlreadyTaken_thenReturnConflict()
    {
        RegisterRequestDTO requestDTO = new RegisterRequestDTO(
                "alfredo@gmail.com",
                "marceloviviani",
                "Gordomono8!",
                "Alfredo",
                "Viviani"
        );

        restTestClient.post()
                .uri("/register")
                .body(requestDTO)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.USERNAME_ALREADY_EXISTS.name())
                .jsonPath("$.message").isEqualTo("Username already exists.");
    }

    @Test
    void register_whenEmailIsAlreadyTaken_thenReturnConflict()
    {
        RegisterRequestDTO requestDTO = new RegisterRequestDTO(
                "marcelo@gmail.com",
                "alfredoviviani",
                "Gordomono8!",
                "Alfredo",
                "Viviani"
        );

        restTestClient.post()
                .uri("/register")
                .body(requestDTO)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.EMAIL_ALREADY_EXISTS.name())
                .jsonPath("$.message").isEqualTo("Email already exists.");
    }

    @Test
    void register_whenFieldsAreInvalid_thenReturnBadRequest()
    {
        RegisterRequestDTO requestDTO = new RegisterRequestDTO(
                "invalid-email",
                "ab",
                "weak",
                "Alfredo123",
                "Viviani123"
        );

        restTestClient.post()
                .uri("/register")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.email").isEqualTo("Invalid email format.")
                .jsonPath("$.username").isEqualTo("Username must be between 3 and 20 characters and contain only letters and numbers.")
                .jsonPath("$.password").isEqualTo("The password must be at least 8 characters long and include at least one uppercase letter, one lowercase letter, one number, and one special character.");
    }

    @Test
    void register_whenFieldsAreMissing_thenReturnBadRequest()
    {
        RegisterRequestDTO requestDTO = new RegisterRequestDTO(
                null,
                null,
                null,
                null,
                null
        );

        restTestClient.post()
                .uri("/register")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.email").isEqualTo("Email is required.")
                .jsonPath("$.username").isEqualTo("Username is required.")
                .jsonPath("$.password").isEqualTo("Password is required.");
    }

    @Test
    void register_whenFieldsAreBlank_thenReturnBadRequest()
    {
        RegisterRequestDTO requestDTO = new RegisterRequestDTO(
                "",
                "",
                "",
                "",
                ""
        );

        restTestClient.post()
                .uri("/register")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.email").isEqualTo("Email is required.")
                .jsonPath("$.username").isNotEmpty()
                .jsonPath("$.password").isNotEmpty();
    }

    @Test
    void login_whenCredentialsAreValid_thenReturnCookies()
    {
        AuthRequestDTO requestDTO = new AuthRequestDTO("marceloviviani", "Gordomono8!");

        restTestClient.post()
                .uri("/login")
                .body(requestDTO)
                .exchange()
                .expectStatus().isNoContent()
                .expectHeader().valuesMatch(
                        HttpHeaders.SET_COOKIE,
                        "access-token=[^;]+;.*",
                        "refresh-token=[^;]+;.*"
                )
                .expectBody().isEmpty();
    }

    @Test
    void login_whenFieldsAreMissing_thenReturnBadRequest()
    {
        AuthRequestDTO requestDTO = new AuthRequestDTO(null, null);

        restTestClient.post()
                .uri("/login")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.username").isEqualTo("Username is required.")
                .jsonPath("$.password").isEqualTo("Password is required.");
    }

    @Test
    void login_whenFieldsAreBlank_thenReturnBadRequest()
    {
        AuthRequestDTO requestDTO = new AuthRequestDTO("", "");

        restTestClient.post()
                .uri("/login")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.username").isEqualTo("Username is required.")
                .jsonPath("$.password").isEqualTo("Password is required.");
    }

    @Test
    void refresh_whenRefreshTokenIsValid_thenReturnAccessCookie()
    {
        UserEntity user = UserFactory.customer();
        user.setUsername("marceloviviani");

        ResponseCookie refreshTokenCookie = jwtService.generateRefreshTokenCookie(user);

        restTestClient.post()
                .uri("/refresh")
                .header(HttpHeaders.COOKIE, refreshTokenCookie.getName() + "=" + refreshTokenCookie.getValue())
                .exchange()
                .expectStatus().isNoContent()
                .expectHeader().valuesMatch(HttpHeaders.SET_COOKIE, "access-token=[^;]+;.*")
                .expectBody().isEmpty();
    }

    @Test
    void refresh_whenAccessTokenIsExpiredAndRefreshTokenIsValid_thenReturnNewAccessCookie()
    {
        UserEntity user = UserFactory.customer();
        user.setUsername("marceloviviani");

        String expiredAccessToken = createExpiredToken(TokenType.ACCESS);
        ResponseCookie refreshTokenCookie = jwtService.generateRefreshTokenCookie(user);

        EntityExchangeResult<Void> result = restTestClient.post()
                .uri("/refresh")
                .header(HttpHeaders.COOKIE, "access-token=" + expiredAccessToken
                        + "; refresh-token=" + refreshTokenCookie.getValue())
                .exchange()
                .expectStatus().isNoContent()
                .expectHeader().valuesMatch(HttpHeaders.SET_COOKIE, "access-token=[^;]+;.*")
                .expectBody().isEmpty();

        String setCookie = result.getResponseHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);

        HttpCookie accessCookie = HttpCookie.parse(setCookie).get(0);
        String newAccessToken = accessCookie.getValue();

        assertEquals("access-token", accessCookie.getName());
        assertNotEquals(expiredAccessToken, newAccessToken);
        assertTrue(jwtService.isValid(newAccessToken, user));
        assertEquals("ACCESS", jwtService.getType(newAccessToken));
        assertEquals(user.getId(), jwtService.getUserId(newAccessToken));
    }

    @Test
    void refresh_whenRefreshTokenIsExpired_thenReturnUnauthorizedWithoutCookies()
    {
        String expiredAccessToken = createExpiredToken(TokenType.ACCESS);
        String expiredRefreshToken = createExpiredToken(TokenType.REFRESH);

        restTestClient.post()
                .uri("/refresh")
                .header(HttpHeaders.COOKIE, "access-token=" + expiredAccessToken
                        + "; refresh-token=" + expiredRefreshToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.INVALID_JWT.name());
    }

    @Test
    void refresh_whenRefreshTokenIsInvalid_thenReturnUnauthorizedWithoutCookies()
    {
        String expiredAccessToken = createExpiredToken(TokenType.ACCESS);

        restTestClient.post()
                .uri("/refresh")
                .header(HttpHeaders.COOKIE, "access-token=" + expiredAccessToken + "; refresh-token=invalid-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.INVALID_JWT.name());
    }

    @Test
    void login_whenAccessTokenCookieIsExpiredAndCredentialsAreValid_thenReturnCookies()
    {
        AuthRequestDTO requestDTO = new AuthRequestDTO("marceloviviani", "Gordomono8!");
        String expiredAccessToken = createExpiredToken(TokenType.ACCESS);

        restTestClient.post()
                .uri("/login")
                .header(HttpHeaders.COOKIE, "access-token=" + expiredAccessToken)
                .body(requestDTO)
                .exchange()
                .expectStatus().isNoContent()
                .expectHeader().valuesMatch(
                        HttpHeaders.SET_COOKIE,
                        "access-token=[^;]+;.*",
                        "refresh-token=[^;]+;.*"
                )
                .expectBody().isEmpty();
    }

    @Test
    void register_whenAccessTokenCookieIsExpiredAndDataIsValid_thenCreateCustomerAndReturnCookies()
    {
        RegisterRequestDTO requestDTO = new RegisterRequestDTO(
                "alfredo@gmail.com",
                "alfredoviviani",
                "Gordomono8!",
                "Alfredo",
                "Viviani"
        );
        String expiredAccessToken = createExpiredToken(TokenType.ACCESS);

        restTestClient.post()
                .uri("/register")
                .header(HttpHeaders.COOKIE, "access-token=" + expiredAccessToken)
                .body(requestDTO)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().valuesMatch(
                        HttpHeaders.SET_COOKIE,
                        "access-token=[^;]+;.*",
                        "refresh-token=[^;]+;.*"
                )
                .expectBody().isEmpty();
    }

    @Test
    void logout_whenUserIsAuthenticated_thenReturnEmptyCookies()
    {
        UserEntity user = UserFactory.customer();
        user.setUsername("marceloviviani");

        ResponseCookie accessTokenCookie = jwtService.generateAccessTokenCookie(user);

        restTestClient.post()
                .uri("/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenCookie.getValue())
                .exchange()
                .expectStatus().isNoContent()
                .expectHeader().valuesMatch(
                        HttpHeaders.SET_COOKIE,
                        "access-token=;.*Max-Age=0;.*",
                        "refresh-token=;.*Max-Age=0;.*"
                )
                .expectBody().isEmpty();
    }

    @Test
    void logout_whenRequestIsUnauthenticated_thenReturnUnauthorized()
    {
        restTestClient.post()
                .uri("/logout")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void logout_whenInvalidToken_thenReturnUnauthorized()
    {
        restTestClient.post()
                .uri("/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer INVALID_TOKEN")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private String createExpiredToken(TokenType type)
    {
        Instant now = Instant.now();

        return Jwts.builder()
                .subject("2")
                .claim("username", "marceloviviani")
                .claim("role", "CUSTOMER")
                .claim("type", type.name())
                .issuedAt(Date.from(now.minusSeconds(1800)))
                .expiration(Date.from(now.minusSeconds(900)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
