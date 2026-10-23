package com.hardwarehub.user_service.controller;

import com.hardwarehub.user_service.config.PostgresTestConfig;
import com.hardwarehub.user_service.dto.PasswordRequestDTO;
import com.hardwarehub.user_service.dto.PasswordUpdateRequestDTO;
import com.hardwarehub.user_service.dto.UserUpdateRequestDTO;
import com.hardwarehub.user_service.enums.ApiError;
import com.hardwarehub.user_service.entity.UserEntity;
import com.hardwarehub.user_service.repository.UserRepository;
import com.hardwarehub.user_service.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.cloud.config.enabled=false")
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@Sql(statements = "DELETE FROM users")
@Sql("/data.sql")
class UserControllerTest
{
    @LocalServerPort
    private int port;

    private RestTestClient restTestClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp()
    {
        UserEntity user = userRepository.findById(2).orElseThrow();

        ResponseCookie accessTokenCookie = jwtService.generateAccessTokenCookie(user);

        restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/api/v1/users")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenCookie.getValue())
                .build();
    }

    @Test
    void getMe_whenUserIsAuthenticated_thenReturnMe()
    {
        restTestClient.get()
                .uri("/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo("marcelo@gmail.com")
                .jsonPath("$.username").isEqualTo("marceloviviani")
                .jsonPath("$.role").isEqualTo("CUSTOMER");
    }

    @Test
    void updateMe_whenDataIsValid_thenUpdateMe()
    {
        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                "alfredo@gmail.com",
                "alfredoviviani",
                "Alfredo",
                "Sarlo"
        );

        restTestClient.patch()
                .uri("/me")
                .body(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo(requestDTO.email())
                .jsonPath("$.username").isEqualTo(requestDTO.username())
                .jsonPath("$.first_name").isEqualTo(requestDTO.firstName());
    }

    @Test
    void updateMe_whenOnlyEmailIsProvided_thenKeepOtherFields()
    {
        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                "alfredo@gmail.com",
                null,
                null,
                null
        );

        restTestClient.patch()
                .uri("/me")
                .body(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo(requestDTO.email())
                .jsonPath("$.username").isEqualTo("marceloviviani")
                .jsonPath("$.first_name").isEqualTo("Marcelo");
    }

    @Test
    void updateMe_whenDataIsUnchanged_thenReturnMe()
    {
        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                "marcelo@gmail.com",
                "marceloviviani",
                "Marcelo",
                "Viviani"
        );

        restTestClient.patch()
                .uri("/me")
                .body(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo(requestDTO.email())
                .jsonPath("$.username").isEqualTo(requestDTO.username())
                .jsonPath("$.last_name").isEqualTo(requestDTO.lastName());
    }

    @Test
    void updateMe_whenEmailIsAlreadyTaken_thenReturnConflict()
    {
        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                "lorenzo@gmail.com",
                null,
                null,
                null
        );

        restTestClient.patch()
                .uri("/me")
                .body(requestDTO)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.EMAIL_ALREADY_EXISTS.name())
                .jsonPath("$.message").isEqualTo("Email already exists.");
    }

    @Test
    void updateMe_whenUsernameIsAlreadyTaken_thenReturnConflict()
    {
        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                null,
                "lorenzosarlo",
                null,
                null
        );

        restTestClient.patch()
                .uri("/me")
                .body(requestDTO)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.USERNAME_ALREADY_EXISTS.name())
                .jsonPath("$.message").isEqualTo("Username already exists.");
    }

    @Test
    void updateMe_whenFieldsAreInvalid_thenReturnBadRequest()
    {
        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                "invalid-email",
                "ab",
                "Marcelo123",
                "Viviani123"
        );

        restTestClient.patch()
                .uri("/me")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.email").isEqualTo("Invalid email format.")
                .jsonPath("$.username").isEqualTo("Username must be between 3 and 20 characters and contain only letters and numbers.")
                .jsonPath("$.firstName").isEqualTo("First name can only contain letters.");
    }

    @Test
    void changePassword_whenPasswordsAreValid_thenChangePassword()
    {
        PasswordUpdateRequestDTO requestDTO = new PasswordUpdateRequestDTO(
                "Gordomono8!",
                "Gordomono9!"
        );

        restTestClient.patch()
                .uri("/me/password")
                .body(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Your password has been successfully changed.");
    }

    @Test
    void changePassword_whenCurrentPasswordIsIncorrect_thenReturnBadRequest()
    {
        PasswordUpdateRequestDTO requestDTO = new PasswordUpdateRequestDTO(
                "WrongPass1!",
                "Gordomono9!"
        );

        restTestClient.patch()
                .uri("/me/password")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.INVALID_CURRENT_PASSWORD.name())
                .jsonPath("$.message").isEqualTo("Your current password does not match.");
    }

    @Test
    void changePassword_whenNewPasswordMatchesCurrentPassword_thenReturnBadRequest()
    {
        PasswordUpdateRequestDTO requestDTO = new PasswordUpdateRequestDTO(
                "Gordomono8!",
                "Gordomono8!"
        );

        restTestClient.patch()
                .uri("/me/password")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.PASSWORD_REUSE_NOT_ALLOWED.name())
                .jsonPath("$.message").isEqualTo("Your new password must be different from your current password.");
    }

    @Test
    void changePassword_whenPasswordsAreMissing_thenReturnBadRequest()
    {
        PasswordUpdateRequestDTO requestDTO = new PasswordUpdateRequestDTO(null, null);

        restTestClient.patch()
                .uri("/me/password")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.currentPassword").isEqualTo("Password is required.")
                .jsonPath("$.newPassword").isEqualTo("Password is required.");
    }

    @Test
    void changePassword_whenFieldsAreInvalid_thenReturnBadRequest()
    {
        PasswordUpdateRequestDTO requestDTO = new PasswordUpdateRequestDTO("", "weak");

        restTestClient.patch()
                .uri("/me/password")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.currentPassword").isEqualTo("Password is required.")
                .jsonPath("$.newPassword").isEqualTo("The password must be at least 8 characters long and include at least one uppercase letter, one lowercase letter, one number, and one special character.");
    }

    @Test
    void deleteMe_whenPasswordIsCorrect_thenDeleteMe()
    {
        PasswordRequestDTO requestDTO = new PasswordRequestDTO("Gordomono8!");

        restTestClient.method(HttpMethod.DELETE)
                .uri("/me")
                .body(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Your account has been successfully deleted.");
    }

    @Test
    void deleteMe_whenPasswordIsIncorrect_thenReturnBadRequest()
    {
        PasswordRequestDTO requestDTO = new PasswordRequestDTO("WrongPass1!");

        restTestClient.method(HttpMethod.DELETE)
                .uri("/me")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.INVALID_CURRENT_PASSWORD.name())
                .jsonPath("$.message").isEqualTo("Invalid password.");
    }

    @Test
    void deleteMe_whenPasswordIsMissing_thenReturnBadRequest()
    {
        PasswordRequestDTO requestDTO = new PasswordRequestDTO(null);

        restTestClient.method(HttpMethod.DELETE)
                .uri("/me")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.password").isEqualTo("Password is required.");
    }

    @Test
    void deleteMe_whenPasswordIsInvalid_thenReturnBadRequest()
    {
        PasswordRequestDTO requestDTO = new PasswordRequestDTO("weak");

        restTestClient.method(HttpMethod.DELETE)
                .uri("/me")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.password").isEqualTo("The password must be at least 8 characters long and include at least one uppercase letter, one lowercase letter, one number, and one special character.");
    }

    @Test
    void changePassword_whenPasswordsAreBlank_thenReturnBadRequest()
    {
        PasswordUpdateRequestDTO requestDTO = new PasswordUpdateRequestDTO("", "");

        restTestClient.patch()
                .uri("/me/password")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.currentPassword").isEqualTo("Password is required.")
                .jsonPath("$.newPassword").isNotEmpty();
    }

    @Test
    void deleteMe_whenPasswordIsBlank_thenReturnBadRequest()
    {
        PasswordRequestDTO requestDTO = new PasswordRequestDTO("");

        restTestClient.method(HttpMethod.DELETE)
                .uri("/me")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.password").isNotEmpty();
    }
}
