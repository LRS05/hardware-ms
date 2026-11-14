package com.hardwarehub.payment_service.repository;

import com.hardwarehub.payment_service.entity.PaymentEntity;
import com.hardwarehub.payment_service.enums.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Integer>
{
    boolean existsByOrderId(int orderId);

    boolean existsByProviderAndProviderReference(PaymentProvider provider, String providerReference);
}
