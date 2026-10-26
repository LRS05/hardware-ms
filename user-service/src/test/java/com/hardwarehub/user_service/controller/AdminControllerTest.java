package com.hardwarehub.user_service.controller;

import com.hardwarehub.user_service.config.PostgresTestConfig;
import com.hardwarehub.user_service.dto.RoleRequestDTO;
import com.hardwarehub.user_service.dto.PasswordUpdateRequestDTO;
import com.hardwarehub.user_service.dto.UserUpdateRequestDTO;
import com.hardwarehub.user_service.enums.ApiError;
import com.hardwarehub.user_service.enums.Role;
import com.hardwarehub.user_service.entity.UserEntity;
import com.hardwarehub.user_service.factory.UserFactory;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@Sql(statements = "DELETE FROM users")
@Sql("/data.sql")
class AdminControllerTest
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
        UserEntity admin = userRepository.findById(1).orElseThrow();

        ResponseCookie accessTokenCookie = jwtService.generateAccessTokenCookie(admin);

        restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/api/v1/admin")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenCookie.getValue())
                .build();

    }

    @Test
    void getMe_whenAdminIsAuthenticated_thenReturnMe()
    {
        restTestClient.get()
                .uri("/me")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo("lorenzo@gmail.com")
                .jsonPath("$.username").isEqualTo("lorenzosarlo")
                .jsonPath("$.role").isEqualTo("ADMIN");
    }

    @Test
    void getUsers_whenNoFiltersAreProvided_thenReturnAllUsers()
    {
        restTestClient.get()
                .uri("/users?sort=id,asc")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.content[0].username").isEqualTo("lorenzosarlo")
                .jsonPath("$.content[1].username").isEqualTo("marceloviviani");
    }

    @Test
    void getUsers_whenPageIsRequested_thenReturnRequestedPage()
    {
        restTestClient.get()
                .uri("/users?page=1&size=1&sort=id,asc")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.number").isEqualTo(1)
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].username").isEqualTo("marceloviviani");
    }

    @Test
    void getUsers_whenUsernameIsProvided_thenReturnMatchingUsers()
    {
        restTestClient.get()
                .uri("/users?username=MARCELO")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].username").isEqualTo("marceloviviani")
                .jsonPath("$.content[0].role").isEqualTo("CUSTOMER");
    }

    @Test
    void getUsers_whenRoleIsProvided_thenReturnUsersWithRole()
    {
        restTestClient.get()
                .uri("/users?role=CUSTOMER")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].username").isEqualTo("marceloviviani")
                .jsonPath("$.content[0].role").isEqualTo("CUSTOMER");
    }

    @Test
    void getUsers_whenNoUsersMatch_thenReturnEmptyPage()
    {
        restTestClient.get()
                .uri("/users?username=missingUser")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(0)
                .jsonPath("$.content.length()").isEqualTo(0);
    }

    @Test
    void getUsers_whenUsernameAndRoleAreProvided_thenFilterByUsername()
    {
        restTestClient.get()
                .uri("/users?username=marcelo&role=ADMIN")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].username").isEqualTo("marceloviviani")
                .jsonPath("$.content[0].role").isEqualTo("CUSTOMER");
    }

    @Test
    void getUsers_whenRoleIsInvalid_thenReturnBadRequest()
    {
        restTestClient.get()
                .uri("/users?role=INVALID")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.METHOD_ARGUMENT_TYPE_MISMATCH.name())
                .jsonPath("$.message").isEqualTo("Invalid parameter type.");
    }

    @Test
    void getUserById_whenUserExists_thenReturnUser()
    {
        restTestClient.get()
                .uri("/users/2")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(2)
                .jsonPath("$.email").isEqualTo("marcelo@gmail.com")
                .jsonPath("$.username").isEqualTo("marceloviviani");
    }

    @Test
    void getUserById_whenIdIsInvalid_thenReturnBadRequest()
    {
        restTestClient.get()
                .uri("/users/invalid")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.METHOD_ARGUMENT_TYPE_MISMATCH.name())
                .jsonPath("$.message").isEqualTo("Invalid parameter type.");
    }

    @Test
    void getUserByEmail_whenUserExists_thenReturnUser()
    {
        restTestClient.get()
                .uri("/users/email/marcelo@gmail.com")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(2)
                .jsonPath("$.email").isEqualTo("marcelo@gmail.com")
                .jsonPath("$.username").isEqualTo("marceloviviani");
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
                .jsonPath("$.username").isEqualTo("lorenzosarlo")
                .jsonPath("$.first_name").isEqualTo("Lorenzo");
    }

    @Test
    void updateMe_whenDataIsUnchanged_thenReturnMe()
    {
        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                "lorenzo@gmail.com",
                "lorenzosarlo",
                "Lorenzo",
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
                .jsonPath("$.last_name").isEqualTo(requestDTO.lastName());
    }

    @Test
    void updateMe_whenEmailIsAlreadyTaken_thenReturnConflict()
    {
        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                "marcelo@gmail.com",
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
                "marceloviviani",
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
    void updateUserById_whenDataIsValid_thenUpdateUser()
    {
        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                "alfredo@gmail.com",
                "alfredoviviani",
                "Alfredo",
                "Sarlo"
        );

        restTestClient.patch()
                .uri("/users/2")
                .body(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo(requestDTO.email())
                .jsonPath("$.username").isEqualTo(requestDTO.username())
                .jsonPath("$.first_name").isEqualTo(requestDTO.firstName());
    }

    @Test
    void updateUserById_whenOnlyEmailIsProvided_thenKeepOtherFields()
    {
        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                "alfredo@gmail.com",
                null,
                null,
                null
        );

        restTestClient.patch()
                .uri("/users/2")
                .body(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo(requestDTO.email())
                .jsonPath("$.username").isEqualTo("marceloviviani")
                .jsonPath("$.first_name").isEqualTo("Marcelo");
    }

    @Test
    void updateUserById_whenDataIsUnchanged_thenReturnUser()
    {
        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                "marcelo@gmail.com",
                "marceloviviani",
                "Marcelo",
                "Viviani"
        );

        restTestClient.patch()
                .uri("/users/2")
                .body(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.email").isEqualTo(requestDTO.email())
                .jsonPath("$.username").isEqualTo(requestDTO.username())
                .jsonPath("$.last_name").isEqualTo(requestDTO.lastName());
    }

    @Test
    void updateUserById_whenEmailIsAlreadyTaken_thenReturnConflict()
    {
        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                "lorenzo@gmail.com",
                null,
                null,
                null
        );

        restTestClient.patch()
                .uri("/users/2")
                .body(requestDTO)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.EMAIL_ALREADY_EXISTS.name())
                .jsonPath("$.message").isEqualTo("Email already exists.");
    }

    @Test
    void updateUserById_whenUsernameIsAlreadyTaken_thenReturnConflict()
    {
        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                null,
                "lorenzosarlo",
                null,
                null
        );

        restTestClient.patch()
                .uri("/users/2")
                .body(requestDTO)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.USERNAME_ALREADY_EXISTS.name())
                .jsonPath("$.message").isEqualTo("Username already exists.");
    }

    @Test
    void updateUserById_whenFieldsAreInvalid_thenReturnBadRequest()
    {
        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                "invalid-email",
                "ab",
                "Marcelo123",
                "Viviani123"
        );

        restTestClient.patch()
                .uri("/users/2")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.email").isEqualTo("Invalid email format.")
                .jsonPath("$.username").isEqualTo("Username must be between 3 and 20 characters and contain only letters and numbers.")
                .jsonPath("$.firstName").isEqualTo("First name can only contain letters.");
    }

    @Test
    void updateUserRoleById_whenRoleIsStaff_thenUpdateRole()
    {
        RoleRequestDTO requestDTO = new RoleRequestDTO(Role.STAFF);

        restTestClient.patch()
                .uri("/users/2/role")
                .body(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(2)
                .jsonPath("$.username").isEqualTo("marceloviviani")
                .jsonPath("$.role").isEqualTo(requestDTO.role().name());
    }

    @Test
    void updateUserRoleById_whenRoleIsAdmin_thenUpdateRole()
    {
        RoleRequestDTO requestDTO = new RoleRequestDTO(Role.ADMIN);

        restTestClient.patch()
                .uri("/users/2/role")
                .body(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(2)
                .jsonPath("$.username").isEqualTo("marceloviviani")
                .jsonPath("$.role").isEqualTo(requestDTO.role().name());
    }

    @Test
    void updateUserRoleById_whenRoleIsUnchanged_thenReturnUser()
    {
        RoleRequestDTO requestDTO = new RoleRequestDTO(Role.CUSTOMER);

        restTestClient.patch()
                .uri("/users/2/role")
                .body(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(2)
                .jsonPath("$.username").isEqualTo("marceloviviani")
                .jsonPath("$.role").isEqualTo(requestDTO.role().name());
    }

    @Test
    void updateUserRoleById_whenRoleIsMissing_thenReturnBadRequest()
    {
        RoleRequestDTO requestDTO = new RoleRequestDTO(null);

        restTestClient.patch()
                .uri("/users/2/role")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.role").isEqualTo("Role is required.");
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
    void deleteUserById_whenUserExists_thenDeleteUser()
    {
        restTestClient.delete()
                .uri("/users/2")
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();
    }

    @Test
    void deleteUserById_whenIdIsInvalid_thenReturnBadRequest()
    {
        restTestClient.delete()
                .uri("/users/invalid")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.METHOD_ARGUMENT_TYPE_MISMATCH.name())
                .jsonPath("$.message").isEqualTo("Invalid parameter type.");
    }

    // Authorization and not found tests

    @Test
    void getMe_whenRequestIsUnauthenticated_thenReturnUnauthorized()
    {
        RestTestClient unauthenticatedRestTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/api/v1/admin")
                .build();

        unauthenticatedRestTestClient.get()
                .uri("/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void getMe_whenCustomerIsAuthenticated_thenReturnForbidden()
    {
        ResponseCookie accessTokenCookie = jwtService.generateAccessTokenCookie(UserFactory.customer());

        RestTestClient customerRestTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/api/v1/admin")
                .build();

        customerRestTestClient.get()
                .uri("/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenCookie.getValue())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void updateUserById_whenStaffIsAuthenticated_thenReturnForbidden()
    {
        int userId = 2;

        UserEntity staff = UserFactory.customer();
        staff.setRole(Role.STAFF);

        ResponseCookie accessTokenCookie = jwtService.generateAccessTokenCookie(staff);

        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                "updated@example.com",
                "updateduser",
                "Updated",
                "User"
        );
        RestTestClient staffRestTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/api/v1/admin")
                .build();

        staffRestTestClient.patch()
                .uri("/users/{id}", userId)
                .body(requestDTO)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenCookie.getValue())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getUserById_whenUserDoesNotExist_thenReturnNotFound()
    {
        int userId = 999;

        ResponseCookie accessTokenCookie = jwtService.generateAccessTokenCookie(UserFactory.admin());

        RestTestClient adminRestTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/api/v1/admin")
                .build();

        adminRestTestClient.get()
                .uri("/users/{id}", userId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokenCookie.getValue())
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.USERNAME_NOT_FOUND.name())
                .jsonPath("$.message").isEqualTo("User not found.");
    }

    @Test
    void accessAdminEndpoints_whenInvalidToken_thenReturnUnauthorized()
    {
        RestTestClient invalidTokenClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/api/v1/admin")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer INVALID_TOKEN")
                .build();

        invalidTokenClient.get()
                .uri("")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
