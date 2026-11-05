package com.hardwarehub.user_service.repository;

import com.hardwarehub.user_service.entity.UserEntity;
import com.hardwarehub.user_service.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Integer>
{
    Page<UserEntity> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

    Page<UserEntity> findAllByRole(Role role, Pageable pageable);

    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);
}
