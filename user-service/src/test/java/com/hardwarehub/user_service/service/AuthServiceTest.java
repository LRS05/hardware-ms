package com.hardwarehub.user_service.service;

import com.hardwarehub.user_service.client.OrderClient;
import com.hardwarehub.user_service.dto.AuthRequestDTO;
import com.hardwarehub.user_service.dto.RegisterRequestDTO;
import com.hardwarehub.user_service.entity.UserEntity;
import com.hardwarehub.user_service.enums.Role;
import com.hardwarehub.user_service.enums.TokenType;
import com.hardwarehub.user_service.exception.CookieNotFoundException;
import com.hardwarehub.user_service.exception.EmailAlreadyExistsException;
import com.hardwarehub.user_service.exception.InvalidJwtException;
import com.hardwarehub.user_service.exception.UsernameAlreadyExistsException;
import com.hardwarehub.user_service.factory.UserFactory;
import com.hardwarehub.user_service.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest
{
    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private OrderClient orderClient;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_whenUsernameAndEmailAreAvailable_thenCreateCustomerAndReturnCookies()
    {
        // Given
        RegisterRequestDTO requestDTO = new RegisterRequestDTO(
                "Marcelo@gmail.com",
                "Marceloviviani",
                "Gordomono8!",
                "Marcelo",
                "Viviani"
        );

        UserEntity user = UserFactory.customer();

        ResponseCookie accessTokenCookie = ResponseCookie.from("access-token", "access-token-value").build();
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refresh-token", "refresh-token-value").build();

        when(userRepository.existsByUsername(requestDTO.username())).thenReturn(false);
        when(userRepository.existsByEmail(requestDTO.email())).thenReturn(false);
        when(passwordEncoder.encode(requestDTO.password())).thenReturn("encoded-password");
        when(userRepository.save(any(UserEntity.class))).thenReturn(user);
        when(jwtService.generateAccessTokenCookie(user)).thenReturn(accessTokenCookie);
        when(jwtService.generateRefreshTokenCookie(user)).thenReturn(refreshTokenCookie);

        // When
        Map<String, ResponseCookie> result = authService.register(requestDTO);

        // Then
        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(captor.capture());

        assertEquals(Role.CUSTOMER, captor.getValue().getRole());
        assertEquals(requestDTO.email(), captor.getValue().getEmail());
        assertEquals(requestDTO.username(), captor.getValue().getUsername());
        assertEquals("encoded-password", captor.getValue().getPassword());
        assertEquals(requestDTO.firstName(), captor.getValue().getFirstName());
        assertEquals(requestDTO.lastName(), captor.getValue().getLastName());

        assertEquals(Map.of(
                "access-token", accessTokenCookie,
                "refresh-token", refreshTokenCookie
        ), result);

        verify(userRepository).existsByUsername(requestDTO.username());
        verify(userRepository).existsByEmail(requestDTO.email());
        verifyNoMoreInteractions(userRepository);
        verify(passwordEncoder).encode(requestDTO.password());
        verifyNoMoreInteractions(passwordEncoder);
        verify(orderClient).createByUserId(user.getId());
        verify(jwtService).generateAccessTokenCookie(user);
        verify(jwtService).generateRefreshTokenCookie(user);
    }

    @Test
    void register_whenUsernameIsAlreadyTaken_thenThrowUsernameAlreadyExistsException()
    {
        // Given
        RegisterRequestDTO requestDTO = new RegisterRequestDTO(
                "marcelo@gmail.com",
                "Marceloviviani",
                "Gordomono8!",
                "Marcelo",
                "Viviani"
        );

        when(userRepository.existsByUsername(requestDTO.username())).thenReturn(true);

        // When
        assertThrows(UsernameAlreadyExistsException.class, () -> authService.register(requestDTO));

        // Then
        verify(userRepository).existsByUsername(requestDTO.username());
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(passwordEncoder, orderClient, jwtService);
    }

    @Test
    void register_whenEmailIsAlreadyTaken_thenThrowEmailAlreadyExistsException()
    {
        // Given
        RegisterRequestDTO requestDTO = new RegisterRequestDTO(
                "marcelo@gmail.com",
                "Marceloviviani",
                "Gordomono8!",
                "Marcelo",
                "Viviani"
        );

        when(userRepository.existsByUsername(requestDTO.username())).thenReturn(false);
        when(userRepository.existsByEmail(requestDTO.email())).thenReturn(true);

        // When
        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(requestDTO));

        // Then
        verify(userRepository).existsByUsername(requestDTO.username());
        verify(userRepository).existsByEmail(requestDTO.email());
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(passwordEncoder, orderClient, jwtService);
    }

    @Test
    void login_whenCredentialsAreValid_thenReturnCookies()
    {
        // Given
        AuthRequestDTO requestDTO = new AuthRequestDTO("Marceloviviani", "Gordomono8!");

        UserEntity user = UserFactory.customer();
        ResponseCookie accessTokenCookie = ResponseCookie.from("access-token", "access-token-value").build();
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refresh-token", "refresh-token-value").build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(requestDTO.username(), null, List.of()));
        when(userRepository.findByUsername(requestDTO.username())).thenReturn(Optional.of(user));
        when(jwtService.generateAccessTokenCookie(user)).thenReturn(accessTokenCookie);
        when(jwtService.generateRefreshTokenCookie(user)).thenReturn(refreshTokenCookie);

        // When
        Map<String, ResponseCookie> result = authService.login(requestDTO);

        // Then
        assertEquals(Map.of(
                "access-token", accessTokenCookie,
                "refresh-token", refreshTokenCookie
        ), result);

        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken(requestDTO.username(), requestDTO.password())
        );
        verify(userRepository).findByUsername(requestDTO.username());
        verify(jwtService).generateAccessTokenCookie(user);
        verify(jwtService).generateRefreshTokenCookie(user);
    }

    @Test
    void login_whenCredentialsAreInvalid_thenThrowBadCredentialsException()
    {
        // Given
        AuthRequestDTO requestDTO = new AuthRequestDTO("Marceloviviani", "WrongPass1!");

        doThrow(new BadCredentialsException("Bad credentials."))
                .when(authenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));

        // When
        assertThrows(BadCredentialsException.class, () -> authService.login(requestDTO));

        // Then
        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken(requestDTO.username(), requestDTO.password())
        );
        verifyNoInteractions(userRepository, jwtService, passwordEncoder, orderClient);
    }

    @Test
    void login_whenAuthenticatedUserDoesNotExist_thenThrowUsernameNotFoundException()
    {
        // Given
        AuthRequestDTO requestDTO = new AuthRequestDTO("missingUser", "Gordomono8!");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(requestDTO.username(), null, List.of()));
        when(userRepository.findByUsername(requestDTO.username())).thenReturn(Optional.empty());

        // When
        assertThrows(UsernameNotFoundException.class, () -> authService.login(requestDTO));

        // Then
        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken(requestDTO.username(), requestDTO.password())
        );
        verify(userRepository).findByUsername(requestDTO.username());
        verifyNoMoreInteractions(authenticationManager, userRepository);
        verifyNoInteractions(jwtService, passwordEncoder, orderClient);
    }

    @Test
    void refresh_whenRefreshTokenIsValid_thenReturnAccessCookie()
    {
        // Given
        int userId = 2;
        String refreshToken = "refresh-token-value";
        Cookie[] cookies = {new Cookie("refresh-token", refreshToken)};

        UserEntity user = UserFactory.customer();
        ResponseCookie accessTokenCookie = ResponseCookie.from("access-token", "new-access-token-value").build();

        when(jwtService.getTokenFromCookies(TokenType.REFRESH, cookies)).thenReturn(refreshToken);
        when(jwtService.getUserId(refreshToken)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtService.getType(refreshToken)).thenReturn("REFRESH");
        when(jwtService.isValid(refreshToken, user)).thenReturn(true);
        when(jwtService.generateAccessTokenCookie(user)).thenReturn(accessTokenCookie);

        // When
        ResponseCookie result = authService.refresh(cookies);

        // Then
        assertEquals(accessTokenCookie, result);

        verify(jwtService).getTokenFromCookies(TokenType.REFRESH, cookies);
        verify(jwtService).getUserId(refreshToken);
        verify(userRepository).findById(userId);
        verify(jwtService).getType(refreshToken);
        verify(jwtService).isValid(refreshToken, user);
        verify(jwtService).generateAccessTokenCookie(user);
    }

    @Test
    void refresh_whenRefreshCookieIsMissing_thenThrowCookieNotFoundException()
    {
        // Given
        Cookie[] cookies = {new Cookie("access-token", "access-token-value")};

        when(jwtService.getTokenFromCookies(TokenType.REFRESH, cookies))
                .thenThrow(new CookieNotFoundException("refresh-token cookie not found."));

        // When
        assertThrows(CookieNotFoundException.class, () -> authService.refresh(cookies));

        // Then
        verify(jwtService).getTokenFromCookies(TokenType.REFRESH, cookies);
        verifyNoMoreInteractions(jwtService);
        verifyNoInteractions(userRepository);
    }

    @Test
    void refresh_whenTokenUserDoesNotExist_thenThrowUsernameNotFoundException()
    {
        // Given
        int userId = 999;
        String refreshToken = "refresh-token-value";
        Cookie[] cookies = {new Cookie("refresh-token", refreshToken)};

        when(jwtService.getTokenFromCookies(TokenType.REFRESH, cookies)).thenReturn(refreshToken);
        when(jwtService.getUserId(refreshToken)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When
        assertThrows(UsernameNotFoundException.class, () -> authService.refresh(cookies));

        // Then
        verify(jwtService).getTokenFromCookies(TokenType.REFRESH, cookies);
        verify(jwtService).getUserId(refreshToken);
        verify(userRepository).findById(userId);
        verifyNoMoreInteractions(jwtService, userRepository);
    }

    @Test
    void refresh_whenTokenTypeIsNotRefresh_thenThrowInvalidJwtException()
    {
        // Given
        int userId = 2;
        String refreshToken = "access-token-value";
        Cookie[] cookies = {new Cookie("refresh-token", refreshToken)};

        UserEntity user = UserFactory.customer();

        when(jwtService.getTokenFromCookies(TokenType.REFRESH, cookies)).thenReturn(refreshToken);
        when(jwtService.getUserId(refreshToken)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtService.getType(refreshToken)).thenReturn("ACCESS");

        // When
        assertThrows(InvalidJwtException.class, () -> authService.refresh(cookies));

        // Then
        verify(jwtService).getTokenFromCookies(TokenType.REFRESH, cookies);
        verify(jwtService).getUserId(refreshToken);
        verify(userRepository).findById(userId);
        verify(jwtService).getType(refreshToken);
        verifyNoMoreInteractions(jwtService, userRepository);
    }

    @Test
    void refresh_whenTokenIsInvalidOrExpired_thenThrowInvalidJwtException()
    {
        // Given
        int userId = 2;
        String refreshToken = "expired-refresh-token-value";
        Cookie[] cookies = {new Cookie("refresh-token", refreshToken)};

        UserEntity user = UserFactory.customer();

        when(jwtService.getTokenFromCookies(TokenType.REFRESH, cookies)).thenReturn(refreshToken);
        when(jwtService.getUserId(refreshToken)).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtService.getType(refreshToken)).thenReturn("REFRESH");
        when(jwtService.isValid(refreshToken, user)).thenReturn(false);

        // When
        assertThrows(InvalidJwtException.class, () -> authService.refresh(cookies));

        // Then
        verify(jwtService).getTokenFromCookies(TokenType.REFRESH, cookies);
        verify(jwtService).getUserId(refreshToken);
        verify(userRepository).findById(userId);
        verify(jwtService).getType(refreshToken);
        verify(jwtService).isValid(refreshToken, user);
        verifyNoMoreInteractions(jwtService, userRepository);
    }

    @Test
    void logout_whenCalled_thenReturnEmptyAccessAndRefreshCookies()
    {
        // Given
        ResponseCookie emptyAccessCookie = ResponseCookie.from("access-token", "").maxAge(0).build();
        ResponseCookie emptyRefreshCookie = ResponseCookie.from("refresh-token", "").maxAge(0).build();

        when(jwtService.emptyCookie("access-token")).thenReturn(emptyAccessCookie);
        when(jwtService.emptyCookie("refresh-token")).thenReturn(emptyRefreshCookie);

        // When
        Map<String, ResponseCookie> result = authService.logout();

        // Then
        assertEquals(Map.of(
                "access-token", emptyAccessCookie,
                "refresh-token", emptyRefreshCookie
        ), result);

        verify(jwtService).emptyCookie("access-token");
        verify(jwtService).emptyCookie("refresh-token");
        verifyNoMoreInteractions(jwtService);
        verifyNoInteractions(userRepository, passwordEncoder, authenticationManager, orderClient);
    }

    @Test
    void refresh_whenSubjectIsInvalid_thenThrowInvalidJwtExceptionWithoutLookingUpUser()
    {
        String refreshToken = "refresh-token-value";
        Cookie[] cookies = {new Cookie("refresh-token", refreshToken)};

        when(jwtService.getTokenFromCookies(TokenType.REFRESH, cookies)).thenReturn(refreshToken);
        when(jwtService.getUserId(refreshToken)).thenThrow(new InvalidJwtException("Invalid JWT subject."));

        assertThrows(InvalidJwtException.class, () -> authService.refresh(cookies));

        verifyNoInteractions(userRepository);
    }
}
