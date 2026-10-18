package com.hardwarehub.order_service.repository;

import com.hardwarehub.order_service.entity.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItemEntity, Integer>
{
    Optional<OrderItemEntity> findByOrderIdAndProductId(int orderId, int productId);

    @Query(
            value = "SELECT oi.* " +
                    "FROM order_items oi " +
                    "INNER JOIN orders o ON oi.order_id = o.id " +
                    "WHERE oi.id = :id " +
                    "AND o.user_id = :userId " +
                    "AND o.status = 'ACTIVE'",
            nativeQuery = true
    )
    Optional<OrderItemEntity> findActiveItemByIdAndUserId(
            @Param("id") int id,
            @Param("userId") int userId
    );
}
