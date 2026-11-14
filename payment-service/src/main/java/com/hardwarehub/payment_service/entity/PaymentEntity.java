package com.hardwarehub.payment_service.entity;

import com.hardwarehub.payment_service.enums.PaymentProvider;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "payments")
public class PaymentEntity
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "order_id", nullable = false)
    private int orderId;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentProvider provider;

    @Column(name = "provider_reference", nullable = false, length = 100)
    private String providerReference;

    @CreationTimestamp
    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;
}
