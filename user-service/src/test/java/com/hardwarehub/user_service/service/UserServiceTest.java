package com.hardwarehub.user_service.service;

import com.hardwarehub.user_service.dto.PasswordRequestDTO;
import com.hardwarehub.user_service.dto.PasswordUpdateRequestDTO;
import com.hardwarehub.user_service.dto.RoleRequestDTO;
import com.hardwarehub.user_service.dto.UserResponseDTO;
import com.hardwarehub.user_service.dto.UserUpdateRequestDTO;
import com.hardwarehub.user_service.entity.UserEntity;
import com.hardwarehub.user_service.enums.Role;
import com.hardwarehub.user_service.exception.EmailAlreadyExistsException;
import com.hardwarehub.user_service.exception.InvalidCurrentPasswordException;
import com.hardwarehub.user_service.exception.PasswordReuseNotAllowedException;
import com.hardwarehub.user_service.exception.UsernameAlreadyExistsException;
import com.hardwarehub.user_service.factory.UserFactory;
import com.hardwarehub.user_service.mapper.UserMapper;
import com.hardwarehub.user_service.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest
{
    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp()
    {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "Lorenzosarlo",
                null,
                Collections.emptyList()
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @AfterEach
    void clearContext()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMe_whenCurrentUserExists_thenReturnUser()
    {
        // Given
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        UserEntity admin = UserFactory.admin();
        UserResponseDTO adminDTO = UserFactory.adminDTO();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(admin));
        when(userMapper.entityToDTO(admin)).thenReturn(adminDTO);

        // When
        UserResponseDTO result = userService.getMe();

        // Then
        assertEquals(adminDTO, result);
    }

    @Test
    void getMe_whenCurrentUserDoesNotExist_thenThrowUsernameNotFoundException()
    {
        // Given
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // When
        assertThrows(UsernameNotFoundException.class, () -> userService.getMe());

        // Then
        verify(userRepository).findByUsername(username);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void getAll_whenUsersExist_thenReturnUserPage()
    {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        UserEntity user = UserFactory.customer();
        UserResponseDTO userDTO = UserFactory.customerDTO();

        Page<UserEntity> users = new PageImpl<>(List.of(user));

        when(userRepository.findAll(pageable)).thenReturn(users);
        when(userMapper.entityToDTO(user)).thenReturn(userDTO);

        // When
        Page<UserResponseDTO> result = userService.getAll(pageable);

        // Then
        assertEquals(List.of(userDTO), result.getContent());
    }

    @Test
    void getAll_whenNoUsersExist_thenReturnEmptyPage()
    {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        when(userRepository.findAll(pageable)).thenReturn(Page.empty(pageable));

        // When
        Page<UserResponseDTO> result = userService.getAll(pageable);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void getAllByUsernameLike_whenMatchingUsersExist_thenReturnUserPage()
    {
        // Given
        String username = "lor";
        Pageable pageable = PageRequest.of(0, 10);

        UserEntity user = UserFactory.customer();
        UserResponseDTO userDTO = UserFactory.customerDTO();

        when(userRepository.findByUsernameContainingIgnoreCase(username, pageable)).thenReturn(new PageImpl<>(List.of(user)));
        when(userMapper.entityToDTO(user)).thenReturn(userDTO);

        // When
        Page<UserResponseDTO> result = userService.getAllByUsernameLike(username, pageable);

        // Then
        assertEquals(List.of(userDTO), result.getContent());
    }

    @Test
    void getAllByUsernameLike_whenNoUsersMatch_thenReturnEmptyPage()
    {
        // Given
        String username = "missing";

        Pageable pageable = PageRequest.of(0, 10);

        when(userRepository.findByUsernameContainingIgnoreCase(username, pageable)).thenReturn(Page.empty(pageable));

        // When
        Page<UserResponseDTO> result = userService.getAllByUsernameLike(username, pageable);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void getAllByRole_whenUsersWithRoleExist_thenReturnUserPage()
    {
        // Given
        Role role = Role.CUSTOMER;

        Pageable pageable = PageRequest.of(0, 10);

        UserEntity user = UserFactory.customer();
        UserResponseDTO userDTO = UserFactory.customerDTO();

        when(userRepository.findAllByRole(role, pageable)).thenReturn(new PageImpl<>(List.of(user)));
        when(userMapper.entityToDTO(user)).thenReturn(userDTO);

        // When
        Page<UserResponseDTO> result = userService.getAllByRole(role, pageable);

        // Then
        assertEquals(List.of(userDTO), result.getContent());
    }

    @Test
    void getAllByRole_whenNoUsersHaveRole_thenReturnEmptyPage()
    {
        // Given
        Role role = Role.CUSTOMER;

        Pageable pageable = PageRequest.of(0, 10);

        when(userRepository.findAllByRole(role, pageable)).thenReturn(Page.empty(pageable));

        // When
        Page<UserResponseDTO> result = userService.getAllByRole(role, pageable);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void getById_whenUserExists_thenReturnUser()
    {
        // Given
        int id = 2;
        UserEntity user = UserFactory.customer();
        UserResponseDTO userDTO = UserFactory.customerDTO();
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userMapper.entityToDTO(user)).thenReturn(userDTO);

        // When
        UserResponseDTO result = userService.getById(id);

        // Then
        assertEquals(userDTO, result);
    }

    @Test
    void getById_whenUserDoesNotExist_thenThrowUsernameNotFoundException()
    {
        // Given
        int id = 999;
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        // When
        assertThrows(UsernameNotFoundException.class, () -> userService.getById(id));

        // Then
        verify(userRepository).findById(id);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void getByEmail_whenUserExists_thenReturnUser()
    {
        // Given
        String email = "marcelo@example.com";

        UserEntity user = UserFactory.customer();
        UserResponseDTO userDTO = UserFactory.customerDTO();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(userMapper.entityToDTO(user)).thenReturn(userDTO);

        // When
        UserResponseDTO result = userService.getByEmail(email);

        // Then
        assertEquals(userDTO, result);
    }

    @Test
    void getByEmail_whenUserDoesNotExist_thenThrowUsernameNotFoundException()
    {
        // Given
        String email = "missing@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        // When
        assertThrows(UsernameNotFoundException.class, () -> userService.getByEmail(email));

        // Then
        verify(userRepository).findByEmail(email);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void updateMe_whenUsernameAndEmailAreAvailable_thenUpdateAndReturnUser()
    {
        // Given
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        UserEntity admin = UserFactory.admin();
        UserEntity adminUpdated = UserFactory.adminUpdated();
        UserResponseDTO adminUpdatedDTO = UserFactory.adminUpdatedDTO();

        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                "eduardo@gmail.com",
                "Eduviviani",
                "Eduardo",
                "Viviani"
        );

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(admin));
        when(userRepository.existsByUsername(requestDTO.username())).thenReturn(false);
        when(userRepository.existsByEmail(requestDTO.email())).thenReturn(false);

        when(userRepository.save(admin)).thenReturn(adminUpdated);

        when(userMapper.entityToDTO(adminUpdated)).thenReturn(adminUpdatedDTO);

        // When
        UserResponseDTO result = userService.updateMe(requestDTO);

        // Then
        assertEquals(adminUpdatedDTO, result);
    }

    @Test
    void updateMe_whenUsernameIsAlreadyTaken_thenThrowUsernameAlreadyExistsException()
    {
        // Given
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        UserEntity user = UserFactory.customer();

        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                null,
                "TakenUser",
                null,
                null
        );

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("TakenUser")).thenReturn(true);

        // When
        assertThrows(UsernameAlreadyExistsException.class, () -> userService.updateMe(requestDTO));

        // Then
        verify(userRepository).findByUsername(username);
        verify(userRepository).existsByUsername("TakenUser");
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void updateMe_whenEmailIsAlreadyTaken_thenThrowEmailAlreadyExistsException()
    {
        // Given
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        UserEntity admin = UserFactory.admin();

        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                "taken@gmail.com",
                null,
                null,
                null
        );

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(admin));
        when(userRepository.existsByEmail(requestDTO.email())).thenReturn(true);

        // When
        assertThrows(EmailAlreadyExistsException.class, () -> userService.updateMe(requestDTO));

        // Then
        verify(userRepository).findByUsername(username);
        verify(userRepository).existsByEmail(requestDTO.email());
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void updateMe_whenOptionalFieldsAreNull_thenKeepCurrentValues()
    {
        // Given
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        UserEntity admin = UserFactory.admin();
        UserResponseDTO adminDTO = UserFactory.adminDTO();

        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                null,
                null,
                null,
                null
        );

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(admin));
        when(userRepository.save(admin)).thenReturn(admin);
        when(userMapper.entityToDTO(admin)).thenReturn(adminDTO);

        // When
        UserResponseDTO result = userService.updateMe(requestDTO);

        // Then
        assertEquals("Lorenzosarlo", admin.getUsername());
        assertEquals("lorenzo@gmail.com", admin.getEmail());
        assertEquals("Lorenzo", admin.getFirstName());
        assertEquals("Sarlo", admin.getLastName());
        assertEquals(adminDTO, result);
    }

    @Test
    void updateMe_whenCurrentUsernameAndEmailAreUnchanged_thenUpdateAndReturnUser()
    {
        // Given
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        UserEntity admin = UserFactory.admin();
        UserResponseDTO adminDTO = UserFactory.adminDTO();

        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                admin.getEmail(),
                admin.getUsername(),
                admin.getFirstName(),
                admin.getLastName()
        );

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(admin));
        when(userRepository.save(admin)).thenReturn(admin);
        when(userMapper.entityToDTO(admin)).thenReturn(adminDTO);

        // When
        UserResponseDTO result = userService.updateMe(requestDTO);

        // Then
        assertEquals(adminDTO, result);
        verify(userRepository).save(admin);
    }

    @Test
    void updateById_whenUserIsNotAdmin_thenUpdateAndReturnUser()
    {
        // Given
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        int id = 2;
        UserEntity user = UserFactory.customer();

        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                "alfredo@gmail.com",
                "Alfredoviviani",
                "Alfredo",
                "Viviani"
        );

        UserResponseDTO userDTO = UserFactory.customerUpdatedDTO();

        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail(requestDTO.email())).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.entityToDTO(user)).thenReturn(userDTO);

        // When
        UserResponseDTO result = userService.updateById(id, requestDTO);

        // Then
        assertEquals(userDTO, result);
    }

    @Test
    void updateById_whenUserIsAdmin_thenThrowAccessDeniedException()
    {
        // Given
        int id = 1;

        UserEntity admin = UserFactory.admin();

        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                null,
                null,
                "Updated",
                null
        );

        when(userRepository.findById(id)).thenReturn(Optional.of(admin));

        // When
        assertThrows(AccessDeniedException.class, () -> userService.updateById(id, requestDTO));

        // Then
        verify(userRepository).findById(id);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void updateById_whenUserDoesNotExist_thenThrowUsernameNotFoundException()
    {
        // Given
        int id = 999;
        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                null,
                null,
                "Updated",
                null
        );

        when(userRepository.findById(id)).thenReturn(Optional.empty());

        // When
        assertThrows(UsernameNotFoundException.class, () -> userService.updateById(id, requestDTO));

        // Then
        verify(userRepository).findById(id);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void updateById_whenUsernameIsAlreadyTaken_thenThrowUsernameAlreadyExistsException()
    {
        // Given
        int id = 2;
        UserEntity user = UserFactory.customer();
        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(null, "TakenUser", null, null);

        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername(requestDTO.username())).thenReturn(true);

        // When
        assertThrows(UsernameAlreadyExistsException.class, () -> userService.updateById(id, requestDTO));

        // Then
        verify(userRepository).findById(id);
        verify(userRepository).existsByUsername(requestDTO.username());
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void updateById_whenEmailIsAlreadyTaken_thenThrowEmailAlreadyExistsException()
    {
        // Given
        int id = 2;

        UserEntity user = UserFactory.customer();

        UserUpdateRequestDTO requestDTO = new UserUpdateRequestDTO(
                "taken@gmail.com",
                null,
                null,
                null
        );

        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail(requestDTO.email())).thenReturn(true);

        // When
        assertThrows(EmailAlreadyExistsException.class, () -> userService.updateById(id, requestDTO));

        // Then
        verify(userRepository).findById(id);
        verify(userRepository).existsByEmail(requestDTO.email());
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void updateRoleById_whenUserIsNotAdmin_thenUpdateAndReturnUser()
    {
        // Given
        int id = 2;
        RoleRequestDTO requestDTO = new RoleRequestDTO(Role.ADMIN);

        UserEntity user = UserFactory.customer();
        Role userRole = user.getRole();
        UserResponseDTO userDTO = UserFactory.customerRoleUpdatedDTO();

        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.entityToDTO(user)).thenReturn(userDTO);

        // When
        UserResponseDTO result = userService.updateRoleById(id, requestDTO);

        // Then
        assertNotEquals(Role.ADMIN, userRole);
        assertEquals(userDTO, result);
        assertEquals(requestDTO.role(), result.role());
    }

    @Test
    void updateRoleById_whenUserIsAdmin_thenThrowAccessDeniedException()
    {
        // Given
        int id = 1;
        UserEntity admin = UserFactory.admin();

        when(userRepository.findById(id)).thenReturn(Optional.of(admin));

        // When
        assertThrows(AccessDeniedException.class, () -> userService.updateRoleById(id, new RoleRequestDTO(Role.CUSTOMER)));

        // Then
        verify(userRepository).findById(id);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void updateRoleById_whenUserDoesNotExist_thenThrowUsernameNotFoundException()
    {
        // Given
        int id = 999;

        when(userRepository.findById(id)).thenReturn(Optional.empty());

        // When
        assertThrows(UsernameNotFoundException.class, () -> userService.updateRoleById(id, new RoleRequestDTO(Role.CUSTOMER)));

        // Then
        verify(userRepository).findById(id);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void changePassword_whenCurrentPasswordIsValidAndNewPasswordIsDifferent_thenSaveNewPassword()
    {
        // Given
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        UserEntity admin = UserFactory.admin();
        admin.setPassword("encodedCurrentPassword");

        PasswordUpdateRequestDTO requestDTO = new PasswordUpdateRequestDTO(
                "Current1!",
                "NewPassword1!"
        );

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(requestDTO.currentPassword(), admin.getPassword())).thenReturn(true);
        when(passwordEncoder.matches(requestDTO.newPassword(), admin.getPassword())).thenReturn(false);
        when(passwordEncoder.encode(requestDTO.newPassword())).thenReturn("encodedNewPassword");

        // When
        userService.changePassword(requestDTO);

        // Then
        assertEquals("encodedNewPassword", admin.getPassword());
        verify(userRepository).save(admin);
    }

    @Test
    void changePassword_whenCurrentPasswordIsInvalid_thenThrowInvalidCurrentPasswordException()
    {
        // Given
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        UserEntity admin = UserFactory.admin();
        admin.setPassword("encodedCurrentPassword");

        PasswordUpdateRequestDTO requestDTO = new PasswordUpdateRequestDTO(
                "WrongPass1!",
                "NewPassword1!"
        );

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(requestDTO.currentPassword(), admin.getPassword())).thenReturn(false);

        // When
        assertThrows(InvalidCurrentPasswordException.class, () -> userService.changePassword(requestDTO));

        // Then
        verify(userRepository).findByUsername(username);
        verifyNoMoreInteractions(userRepository);
        verify(passwordEncoder).matches(requestDTO.currentPassword(), admin.getPassword());
        verifyNoMoreInteractions(passwordEncoder);
    }

    @Test
    void changePassword_whenNewPasswordMatchesCurrentPassword_thenThrowPasswordReuseNotAllowedException()
    {
        // Given
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        UserEntity admin = UserFactory.admin();
        admin.setPassword("encodedCurrentPassword");

        PasswordUpdateRequestDTO requestDTO = new PasswordUpdateRequestDTO("Current1!", "Current1!");

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(requestDTO.currentPassword(), admin.getPassword())).thenReturn(true, true);

        // When
        assertThrows(PasswordReuseNotAllowedException.class, () -> userService.changePassword(requestDTO));

        // Then
        verify(userRepository).findByUsername(username);
        verifyNoMoreInteractions(userRepository);
        verify(passwordEncoder, times(2)).matches("Current1!", admin.getPassword());
        verifyNoMoreInteractions(passwordEncoder);
    }

    @Test
    void changePassword_whenCurrentUserDoesNotExist_thenThrowUsernameNotFoundException()
    {
        // Given
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        PasswordUpdateRequestDTO requestDTO = new PasswordUpdateRequestDTO("Current1!", "NewPassword1!");

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // When
        assertThrows(UsernameNotFoundException.class, () -> userService.changePassword(requestDTO));

        // Then
        verify(userRepository).findByUsername(username);
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void deleteMe_whenPasswordIsValid_thenDeleteCurrentUser()
    {
        // Given
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        UserEntity admin = UserFactory.admin();
        admin.setPassword("encodedCurrentPassword");

        PasswordRequestDTO requestDTO = new PasswordRequestDTO("Current1!");

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(requestDTO.password(), admin.getPassword())).thenReturn(true);

        // When
        userService.deleteMe(requestDTO);

        // Then
        verify(userRepository).delete(admin);
    }

    @Test
    void deleteMe_whenPasswordIsInvalid_thenThrowInvalidCurrentPasswordException()
    {
        // Given
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        UserEntity admin = UserFactory.admin();
        admin.setPassword("encodedCurrentPassword");

        PasswordRequestDTO requestDTO = new PasswordRequestDTO("WrongPass1!");

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(requestDTO.password(), admin.getPassword())).thenReturn(false);

        // When
        assertThrows(InvalidCurrentPasswordException.class, () -> userService.deleteMe(requestDTO));

        // Then
        verify(userRepository).findByUsername(username);
        verifyNoMoreInteractions(userRepository);
        verify(passwordEncoder).matches(requestDTO.password(), admin.getPassword());
        verifyNoMoreInteractions(passwordEncoder);
    }

    @Test
    void deleteMe_whenCurrentUserDoesNotExist_thenThrowUsernameNotFoundException()
    {
        // Given
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        PasswordRequestDTO requestDTO = new PasswordRequestDTO("Current1!");

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // When
        assertThrows(UsernameNotFoundException.class, () -> userService.deleteMe(requestDTO));

        // Then
        verify(userRepository).findByUsername(username);
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void deleteById_whenUserIsNotAdmin_thenDeleteUser()
    {
        // Given
        int id = 2;

        UserEntity user = UserFactory.customer();

        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        // When
        userService.deleteById(id);

        // Then
        verify(userRepository).delete(user);
    }

    @Test
    void deleteById_whenUserIsAdmin_thenThrowAccessDeniedException()
    {
        // Given
        int id = 1;

        UserEntity admin = UserFactory.admin();

        when(userRepository.findById(id)).thenReturn(Optional.of(admin));

        // When
        assertThrows(AccessDeniedException.class, () -> userService.deleteById(id));

        // Then
        verify(userRepository).findById(id);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void deleteById_whenUserDoesNotExist_thenThrowUsernameNotFoundException()
    {
        // Given
        int id = 999;

        when(userRepository.findById(id)).thenReturn(Optional.empty());

        // When
        assertThrows(UsernameNotFoundException.class, () -> userService.deleteById(id));

        // Then
        verify(userRepository).findById(id);
        verifyNoMoreInteractions(userRepository);
    }
}
