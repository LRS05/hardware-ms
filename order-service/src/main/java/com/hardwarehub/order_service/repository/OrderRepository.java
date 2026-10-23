package com.hardwarehub.order_service.repository;

import com.hardwarehub.order_service.entity.OrderEntity;
import com.hardwarehub.order_service.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Integer>
{
    Optional<OrderEntity> findByUserIdAndStatus(int userId, OrderStatus status);

    Page<OrderEntity> findAllByUserIdAndStatusNotOrderByCreatedAtDesc(
            int userId,
            OrderStatus status,
            Pageable pageable
    );

    Optional<OrderEntity> findByIdAndUserId(int id, int userId);

    Page<OrderEntity> findAllByUserIdOrderByCreatedAtDesc(int userId, Pageable pageable);

    Page<OrderEntity> findAllByUserIdAndStatusOrderByCreatedAtDesc(int userId, OrderStatus status, Pageable pageable);

    boolean existsByUserIdAndStatus(int userId, OrderStatus status);
}
