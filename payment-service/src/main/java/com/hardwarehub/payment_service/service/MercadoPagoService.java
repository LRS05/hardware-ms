package com.hardwarehub.payment_service.service;

import com.hardwarehub.payment_service.exception.InvalidPaymentRequestException;
import com.hardwarehub.payment_service.exception.InvalidPaymentStateException;
import com.hardwarehub.payment_service.exception.PaymentAlreadyCompletedException;
import com.hardwarehub.payment_service.exception.PaymentAttemptNotFoundException;
import com.hardwarehub.payment_service.exception.PaymentProviderDataMismatchException;
import com.hardwarehub.payment_service.exception.PaymentProviderIntegrationException;

import com.hardwarehub.payment_service.client.OrderClient;
import com.hardwarehub.payment_service.dto.*;
import com.hardwarehub.payment_service.entity.MercadoPagoPaymentEntity;
import com.hardwarehub.payment_service.entity.PaymentEntity;
import com.hardwarehub.payment_service.enums.MercadoPagoOrderStatus;
import com.hardwarehub.payment_service.enums.MercadoPagoOrderStatusDetail;
import com.hardwarehub.payment_service.enums.PaymentProvider;
import com.hardwarehub.payment_service.enums.PaymentStatus;
import com.hardwarehub.payment_service.repository.MercadoPagoRepository;
import com.hardwarehub.payment_service.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MercadoPagoService implements PaymentServiceProvider
{
    private final MercadoPagoRepository mercadoPagoRepository;
    private final PaymentRepository paymentRepository;
    private final RestClient restClient;
    private final OrderClient orderClient;
    private final TransactionTemplate transactionTemplate;

    public MercadoPagoService(
            MercadoPagoRepository mercadoPagoRepository,
            PaymentRepository paymentRepository,
            OrderClient orderClient,
            TransactionTemplate transactionTemplate,
            @Value("${mercado-pago.access-token}") String accessToken
    )
    {
        this.mercadoPagoRepository = mercadoPagoRepository;
        this.paymentRepository = paymentRepository;
        this.orderClient = orderClient;
        this.transactionTemplate = transactionTemplate;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .baseUrl("https://api.mercadopago.com")
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();
    }

    @Override
    public PaymentLinkResponseDTO createCheckout(PaymentRequestDTO requestDTO)
    {
        validateCheckoutRequest(requestDTO);

        BigDecimal amount = requestDTO.amount().setScale(2, RoundingMode.UNNECESSARY);

        MercadoPagoPaymentEntity payment = findOrCreateCheckoutAttempt(
                requestDTO.orderId(), amount, requestDTO.expiresAt()
        );

        if (payment.getCheckoutUrl() != null)
        {
            return new PaymentLinkResponseDTO(payment.getCheckoutUrl());
        }

        return createProviderCheckout(payment);
    }

    @Override
    public void cancelCheckout(int orderId)
    {
        if (paymentRepository.existsByOrderId(orderId))
        {
            throw new PaymentAlreadyCompletedException("A completed payment cannot be canceled as a checkout.");
        }

        List<MercadoPagoPaymentEntity> attempts = mercadoPagoRepository.findAllByOrderIdOrderByCreatedAtDesc(orderId);

        validateAttemptsCanBeCanceled(attempts);

        for (MercadoPagoPaymentEntity payment : attempts)
        {
            if (payment.getStatus() != PaymentStatus.CANCELLED)
            {
                cancelPaymentAttempt(payment);
            }
        }
    }

    // Finds the local payment attempt, fetches the order from Mercado Pago, and validates that they match.
    public void processNotification(String providerOrderId)
    {
        Integer orderId = transactionTemplate.execute(
                transactionStatus -> processPaymentNotification(providerOrderId)
        );

        if (orderId != null)
        {
            orderClient.completeOrder(orderId);
        }
    }

    @Override
    public PaymentProvider getProvider()
    {
        return PaymentProvider.MERCADO_PAGO;
    }

    private void validateCheckoutRequest(PaymentRequestDTO requestDTO)
    {
        if (requestDTO.provider() != PaymentProvider.MERCADO_PAGO
                || requestDTO.amount() == null || requestDTO.amount().signum() <= 0)
        {
            throw new InvalidPaymentRequestException("Invalid payment request.");
        }

        if (paymentRepository.existsByOrderId(requestDTO.orderId()))
        {
            throw new PaymentAlreadyCompletedException("The order has already been paid.");
        }
    }

    private MercadoPagoPaymentEntity findOrCreateCheckoutAttempt(int orderId, BigDecimal amount, Instant expiresAt)
    {
        MercadoPagoPaymentEntity payment = mercadoPagoRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(
                orderId, PaymentStatus.PENDING)
                .orElseGet(() -> createAttempt(orderId, amount, expiresAt));

        if (payment.getAmount().compareTo(amount) != 0)
        {
            throw new InvalidPaymentStateException("Pending payment amount does not match the requested amount.");
        }

        if (payment.getCheckoutUrl() == null)
        {
            return payment;
        }

        MercadoPagoOrderResponseDTO existingOrder = fetchMercadoPagoOrderById(payment.getProviderOrderId());

        validateOrderMatchesPaymentAttempt(payment, existingOrder);

        if (existingOrder.status() == MercadoPagoOrderStatus.CREATED)
        {
            return payment;
        }

        if (existingOrder.status() != MercadoPagoOrderStatus.CANCELED)
        {
            throw new InvalidPaymentStateException("The existing Mercado Pago checkout cannot be reused or replaced.");
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        mercadoPagoRepository.save(payment);

        return createAttempt(orderId, amount, expiresAt);
    }

    private PaymentLinkResponseDTO createProviderCheckout(MercadoPagoPaymentEntity payment)
    {
        MercadoPagoRequestDTO mercadoPagoRequestDTO = new MercadoPagoRequestDTO(
                "online",
                "manual",
                payment.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                "mp-attempt-" + payment.getId(),
                "Order " + payment.getOrderId(),
                getExpirationTime(payment.getExpiresAt())
        );

        MercadoPagoOrderResponseDTO response = restClient.post()
                .uri("/v1/orders")
                .header("X-Idempotency-Key", payment.getIdempotencyKey())
                .body(mercadoPagoRequestDTO)
                .retrieve()
                .body(MercadoPagoOrderResponseDTO.class);

        if (response == null || response.id() == null || response.id().isBlank()
                || response.checkoutUrl() == null || response.checkoutUrl().isBlank())
        {
            throw new PaymentProviderIntegrationException("Mercado Pago returned an incomplete order.");
        }

        payment.setProviderOrderId(response.id());
        payment.setCheckoutUrl(response.checkoutUrl());
        mercadoPagoRepository.save(payment);

        return new PaymentLinkResponseDTO(response.checkoutUrl());
    }

    private String getExpirationTime(Instant expiresAt)
    {
        long remainingSeconds = Duration.between(Instant.now(), expiresAt).getSeconds();

        if (remainingSeconds <= 0)
        {
            throw new InvalidPaymentRequestException("The payment deadline has expired.");
        }

        return Duration.ofSeconds(remainingSeconds).toString();
    }

    private void validateAttemptsCanBeCanceled(List<MercadoPagoPaymentEntity> attempts)
    {
        for (MercadoPagoPaymentEntity payment : attempts)
        {
            if (payment.getStatus() == PaymentStatus.CANCELLED)
            {
                continue;
            }

            if (payment.getStatus() != PaymentStatus.PENDING
                    || payment.getProviderOrderId() == null)
            {
                throw new InvalidPaymentStateException("The payment attempt cannot be safely canceled.");
            }
        }
    }

    private void cancelPaymentAttempt(MercadoPagoPaymentEntity payment)
    {
        cancelProviderOrder(payment);
        markAttemptCanceled(payment);
    }

    private void cancelProviderOrder(MercadoPagoPaymentEntity payment)
    {
        String cancellationKey = UUID.nameUUIDFromBytes(
                ("cancel-" + payment.getIdempotencyKey()).getBytes(StandardCharsets.UTF_8)
        ).toString();

        MercadoPagoCancelResponseDTO responseDTO = restClient.post()
                .uri("/v1/orders/{orderId}/cancel", payment.getProviderOrderId())
                .header("X-Idempotency-Key", cancellationKey)
                .retrieve()
                .body(MercadoPagoCancelResponseDTO.class);

        if (responseDTO == null || !payment.getProviderOrderId().equals(responseDTO.id())
                || responseDTO.status() != MercadoPagoOrderStatus.CANCELED)
        {
            throw new PaymentProviderIntegrationException("Mercado Pago did not confirm the cancellation.");
        }
    }

    private void markAttemptCanceled(MercadoPagoPaymentEntity payment)
    {
        payment.setStatus(PaymentStatus.CANCELLED);
        mercadoPagoRepository.save(payment);
    }

    private Integer processPaymentNotification(String providerOrderId)
    {
        MercadoPagoPaymentEntity payment = findByProviderOrderIdOrThrow(providerOrderId);

        MercadoPagoOrderResponseDTO providerOrder = fetchMercadoPagoOrderById(providerOrderId);

        validateOrderMatchesPaymentAttempt(payment, providerOrder);

        if (!isPaymentAccredited(providerOrder))
        {
            return null;
        }

        validatePaidAmount(payment, providerOrder);

        registerApprovedPayment(payment);

        return payment.getOrderId();
    }

    private MercadoPagoOrderResponseDTO fetchMercadoPagoOrderById(String providerOrderId)
    {
        MercadoPagoOrderResponseDTO responseDTO = restClient.get()
                .uri("/v1/orders/{id}", providerOrderId)
                .retrieve()
                .body(MercadoPagoOrderResponseDTO.class);

        if (responseDTO == null || !providerOrderId.equals(responseDTO.id()))
        {
            throw new PaymentProviderIntegrationException("Mercado Pago returned an invalid order.");
        }

        return responseDTO;
    }

    private MercadoPagoPaymentEntity createAttempt(int orderId, BigDecimal amount, Instant expiresAt)
    {
        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .orderId(orderId)
                .idempotencyKey(UUID.randomUUID().toString())
                .amount(amount)
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .expiresAt(expiresAt)
                .build();

        return mercadoPagoRepository.saveAndFlush(payment);
    }

    private void registerApprovedPayment(MercadoPagoPaymentEntity paymentEntity)
    {
        boolean alreadyRegistered = paymentRepository.existsByProviderAndProviderReference(
                PaymentProvider.MERCADO_PAGO,
                paymentEntity.getProviderOrderId()
        );

        if (!alreadyRegistered)
        {
            PaymentEntity newPayment = PaymentEntity.builder()
                    .orderId(paymentEntity.getOrderId())
                    .amount(paymentEntity.getAmount())
                    .currency(paymentEntity.getCurrency())
                    .provider(PaymentProvider.MERCADO_PAGO)
                    .providerReference(paymentEntity.getProviderOrderId())
                    .build();

            paymentRepository.save(newPayment);
        }

        paymentEntity.setStatus(PaymentStatus.APPROVED);
        mercadoPagoRepository.save(paymentEntity);
    }

    private void validateOrderMatchesPaymentAttempt(MercadoPagoPaymentEntity payment, MercadoPagoOrderResponseDTO responseDTO)
    {
        String expectedReference = "mp-attempt-" + payment.getId();

        if (!expectedReference.equals(responseDTO.externalReference()))
        {
            throw new PaymentProviderDataMismatchException("Mercado Pago order reference does not match the payment attempt.");
        }

        if (!payment.getCurrency().equals(responseDTO.currency()))
        {
            throw new PaymentProviderDataMismatchException("Mercado Pago order currency does not match the payment attempt.");
        }

        if (responseDTO.totalAmount() == null
                || payment.getAmount().compareTo(responseDTO.totalAmount()) != 0)
        {
            throw new PaymentProviderDataMismatchException("Mercado Pago order amount does not match the payment attempt.");
        }
    }

    private void validatePaidAmount(MercadoPagoPaymentEntity payment, MercadoPagoOrderResponseDTO responseDTO)
    {
        if (responseDTO.totalPaidAmount() == null
                || payment.getAmount().compareTo(responseDTO.totalPaidAmount()) != 0)
        {
            throw new PaymentProviderDataMismatchException("Mercado Pago paid amount does not match the payment attempt.");
        }
    }

    private MercadoPagoPaymentEntity findByProviderOrderIdOrThrow(String providerOrderId)
    {
        return mercadoPagoRepository.findByProviderOrderId(providerOrderId)
                .orElseThrow(() -> new PaymentAttemptNotFoundException("Payment attempt not found."));
    }

    private boolean isPaymentAccredited(MercadoPagoOrderResponseDTO responseDTO)
    {
        return responseDTO.status() == MercadoPagoOrderStatus.PROCESSED &&
                responseDTO.statusDetail() == MercadoPagoOrderStatusDetail.ACCREDITED;
    }
}
