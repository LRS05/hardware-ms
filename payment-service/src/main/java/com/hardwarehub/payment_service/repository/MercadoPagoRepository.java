package com.hardwarehub.payment_service.repository;

import com.hardwarehub.payment_service.entity.MercadoPagoPaymentEntity;
import com.hardwarehub.payment_service.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MercadoPagoRepository extends JpaRepository<MercadoPagoPaymentEntity, Integer>
{
    Optional<MercadoPagoPaymentEntity> findFirstByOrderIdAndStatusOrderByCreatedAtDesc(int orderId, PaymentStatus status);

    List<MercadoPagoPaymentEntity> findAllByOrderIdOrderByCreatedAtDesc(int orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MercadoPagoPaymentEntity> findByProviderOrderId(String providerOrderId);
}
