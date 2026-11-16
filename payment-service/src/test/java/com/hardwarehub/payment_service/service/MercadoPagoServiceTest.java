package com.hardwarehub.payment_service.service;

import com.hardwarehub.payment_service.exception.InvalidPaymentStateException;
import com.hardwarehub.payment_service.exception.PaymentAttemptNotFoundException;
import com.hardwarehub.payment_service.exception.PaymentProviderDataMismatchException;
import com.hardwarehub.payment_service.exception.PaymentProviderIntegrationException;

import com.hardwarehub.payment_service.client.OrderClient;
import com.hardwarehub.payment_service.dto.MercadoPagoOrderResponseDTO;
import com.hardwarehub.payment_service.dto.MercadoPagoRequestDTO;
import com.hardwarehub.payment_service.dto.PaymentLinkResponseDTO;
import com.hardwarehub.payment_service.dto.PaymentRequestDTO;
import com.hardwarehub.payment_service.entity.MercadoPagoPaymentEntity;
import com.hardwarehub.payment_service.entity.PaymentEntity;
import com.hardwarehub.payment_service.enums.MercadoPagoOrderStatus;
import com.hardwarehub.payment_service.enums.MercadoPagoOrderStatusDetail;
import com.hardwarehub.payment_service.enums.PaymentProvider;
import com.hardwarehub.payment_service.enums.PaymentStatus;
import com.hardwarehub.payment_service.repository.MercadoPagoRepository;
import com.hardwarehub.payment_service.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MercadoPagoServiceTest
{
    @Mock
    private MercadoPagoRepository mercadoPagoRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderClient orderClient;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    @Mock
    private RestClient restClient;

    @Mock
    @SuppressWarnings("rawtypes")
    private RestClient.RequestHeadersUriSpec requestSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodySpec;

    private MercadoPagoService mercadoPagoService;

    @BeforeEach
    void setUp()
    {
        mercadoPagoService = new MercadoPagoService(
                mercadoPagoRepository, paymentRepository, orderClient,
                new TransactionTemplate(transactionManager), "test-token");

        // The service creates its own RestClient, so replace it with the mock.
        ReflectionTestUtils.setField(mercadoPagoService, "restClient", restClient);
    }

    @Test
    void processNotification_whenPaymentIsAccredited_thenRegisterPaymentAndCompleteOrder()
    {
        // Given
        String providerOrderId = "mp-order-123";

        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .id(1)
                .orderId(10)
                .providerOrderId(providerOrderId)
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build();

        MercadoPagoOrderResponseDTO responseDTO = new MercadoPagoOrderResponseDTO(
                providerOrderId, null, MercadoPagoOrderStatus.PROCESSED,
                MercadoPagoOrderStatusDetail.ACCREDITED, "mp-attempt-1",
                new BigDecimal("1500.00"), new BigDecimal("1500.00"), "ARS");


        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(mercadoPagoRepository.findByProviderOrderId(providerOrderId)).thenReturn(Optional.of(payment));
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri("/v1/orders/{id}", providerOrderId)).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MercadoPagoOrderResponseDTO.class)).thenReturn(responseDTO);
        when(paymentRepository.existsByProviderAndProviderReference(
                PaymentProvider.MERCADO_PAGO, providerOrderId)).thenReturn(false);

        // When
        mercadoPagoService.processNotification(providerOrderId);

        // Then
        ArgumentCaptor<PaymentEntity> captor = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).save(captor.capture());

        PaymentEntity result = captor.getValue();
        assertEquals(10, result.getOrderId());
        assertEquals(new BigDecimal("1500.00"), result.getAmount());
        assertEquals("ARS", result.getCurrency());
        assertEquals(PaymentProvider.MERCADO_PAGO, result.getProvider());
        assertEquals(providerOrderId, result.getProviderReference());
        assertEquals(PaymentStatus.APPROVED, payment.getStatus());

        verify(mercadoPagoRepository).save(payment);
        verify(orderClient).completeOrder(10);
    }

    @Test
    void processNotification_whenPaymentIsAlreadyRegistered_thenDoNotDuplicatePayment()
    {
        // Given
        String providerOrderId = "mp-order-123";

        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .id(1)
                .orderId(10)
                .providerOrderId(providerOrderId)
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.APPROVED)
                .build();

        MercadoPagoOrderResponseDTO responseDTO = new MercadoPagoOrderResponseDTO(
                providerOrderId, null, MercadoPagoOrderStatus.PROCESSED,
                MercadoPagoOrderStatusDetail.ACCREDITED, "mp-attempt-1",
                new BigDecimal("1500.00"), new BigDecimal("1500.00"), "ARS");

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(mercadoPagoRepository.findByProviderOrderId(providerOrderId)).thenReturn(Optional.of(payment));
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri("/v1/orders/{id}", providerOrderId)).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MercadoPagoOrderResponseDTO.class)).thenReturn(responseDTO);
        when(paymentRepository.existsByProviderAndProviderReference(
                PaymentProvider.MERCADO_PAGO, providerOrderId)).thenReturn(true);

        // When
        mercadoPagoService.processNotification(providerOrderId);

        // Then
        assertEquals(PaymentStatus.APPROVED, payment.getStatus());

        verify(paymentRepository, never()).save(any(PaymentEntity.class));
        verify(mercadoPagoRepository).save(payment);
        verify(orderClient).completeOrder(10);
    }

    @Test
    void processNotification_whenOrderIsStillProcessing_thenDoNotRegisterPaymentOrCompleteOrder()
    {
        // Given
        String providerOrderId = "mp-order-123";

        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .id(1)
                .orderId(10)
                .providerOrderId(providerOrderId)
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build();

        MercadoPagoOrderResponseDTO responseDTO = new MercadoPagoOrderResponseDTO(
                providerOrderId, null, MercadoPagoOrderStatus.PROCESSING,
                MercadoPagoOrderStatusDetail.IN_PROCESS, "mp-attempt-1",
                new BigDecimal("1500.00"), BigDecimal.ZERO, "ARS");

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(mercadoPagoRepository.findByProviderOrderId(providerOrderId)).thenReturn(Optional.of(payment));
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri("/v1/orders/{id}", providerOrderId)).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MercadoPagoOrderResponseDTO.class)).thenReturn(responseDTO);

        // When
        mercadoPagoService.processNotification(providerOrderId);

        // Then
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        verifyNoInteractions(paymentRepository, orderClient);
        verify(mercadoPagoRepository, never()).save(any());
    }

    @Test
    void processNotification_whenPaymentIsNotAccredited_thenDoNotRegisterPaymentOrCompleteOrder()
    {
        // Given
        String providerOrderId = "mp-order-123";

        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .id(1)
                .orderId(10)
                .providerOrderId(providerOrderId)
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build();

        MercadoPagoOrderResponseDTO responseDTO = new MercadoPagoOrderResponseDTO(
                providerOrderId, null, MercadoPagoOrderStatus.PROCESSED,
                MercadoPagoOrderStatusDetail.WAITING_CAPTURE, "mp-attempt-1",
                new BigDecimal("1500.00"), BigDecimal.ZERO, "ARS");

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(mercadoPagoRepository.findByProviderOrderId(providerOrderId)).thenReturn(Optional.of(payment));
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri("/v1/orders/{id}", providerOrderId)).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MercadoPagoOrderResponseDTO.class)).thenReturn(responseDTO);

        // When
        mercadoPagoService.processNotification(providerOrderId);

        // Then
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        verifyNoInteractions(paymentRepository, orderClient);
        verify(mercadoPagoRepository, never()).save(any());
    }

    @Test
    void processNotification_whenPaymentAttemptDoesNotExist_thenThrowPaymentAttemptNotFoundException()
    {
        // Given
        String providerOrderId = "mp-order-123";

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(mercadoPagoRepository.findByProviderOrderId(providerOrderId)).thenReturn(Optional.empty());

        // When
        assertThrows(PaymentAttemptNotFoundException.class,
                () -> mercadoPagoService.processNotification(providerOrderId));

        // Then
        verify(mercadoPagoRepository).findByProviderOrderId(providerOrderId);
        verifyNoInteractions(restClient, paymentRepository, orderClient);
    }

    @Test
    void processNotification_whenProviderOrderIdDoesNotMatch_thenThrowPaymentProviderIntegrationException()
    {
        // Given
        String providerOrderId = "mp-order-123";

        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .id(1)
                .orderId(10)
                .providerOrderId(providerOrderId)
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build();

        MercadoPagoOrderResponseDTO responseDTO = new MercadoPagoOrderResponseDTO(
                "another-order", null, MercadoPagoOrderStatus.PROCESSED,
                MercadoPagoOrderStatusDetail.ACCREDITED, "mp-attempt-1",
                new BigDecimal("1500.00"), new BigDecimal("1500.00"), "ARS");

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(mercadoPagoRepository.findByProviderOrderId(providerOrderId)).thenReturn(Optional.of(payment));
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri("/v1/orders/{id}", providerOrderId)).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MercadoPagoOrderResponseDTO.class)).thenReturn(responseDTO);

        // When
        assertThrows(PaymentProviderIntegrationException.class,
                () -> mercadoPagoService.processNotification(providerOrderId));

        // Then
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        verifyNoInteractions(paymentRepository, orderClient);
        verify(mercadoPagoRepository, never()).save(any());
    }

    @Test
    void processNotification_whenReferenceDoesNotMatch_thenThrowPaymentProviderDataMismatchException()
    {
        // Given
        String providerOrderId = "mp-order-123";

        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .id(1)
                .orderId(10)
                .providerOrderId(providerOrderId)
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build();

        MercadoPagoOrderResponseDTO responseDTO = new MercadoPagoOrderResponseDTO(
                providerOrderId, null, MercadoPagoOrderStatus.PROCESSED,
                MercadoPagoOrderStatusDetail.ACCREDITED, "mp-attempt-99",
                new BigDecimal("1500.00"), new BigDecimal("1500.00"), "ARS");

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(mercadoPagoRepository.findByProviderOrderId(providerOrderId)).thenReturn(Optional.of(payment));
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri("/v1/orders/{id}", providerOrderId)).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MercadoPagoOrderResponseDTO.class)).thenReturn(responseDTO);

        // When
        assertThrows(PaymentProviderDataMismatchException.class,
                () -> mercadoPagoService.processNotification(providerOrderId));

        // Then
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        verifyNoInteractions(paymentRepository, orderClient);
        verify(mercadoPagoRepository, never()).save(any());
    }

    @Test
    void processNotification_whenCurrencyDoesNotMatch_thenThrowPaymentProviderDataMismatchException()
    {
        // Given
        String providerOrderId = "mp-order-123";

        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .id(1)
                .orderId(10)
                .providerOrderId(providerOrderId)
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build();

        MercadoPagoOrderResponseDTO responseDTO = new MercadoPagoOrderResponseDTO(
                providerOrderId, null, MercadoPagoOrderStatus.PROCESSED,
                MercadoPagoOrderStatusDetail.ACCREDITED, "mp-attempt-1",
                new BigDecimal("1500.00"), new BigDecimal("1500.00"), "USD");

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(mercadoPagoRepository.findByProviderOrderId(providerOrderId)).thenReturn(Optional.of(payment));
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri("/v1/orders/{id}", providerOrderId)).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MercadoPagoOrderResponseDTO.class)).thenReturn(responseDTO);

        // When
        assertThrows(PaymentProviderDataMismatchException.class,
                () -> mercadoPagoService.processNotification(providerOrderId));

        // Then
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        verifyNoInteractions(paymentRepository, orderClient);
        verify(mercadoPagoRepository, never()).save(any());
    }

    @Test
    void processNotification_whenTotalAmountDoesNotMatch_thenThrowPaymentProviderDataMismatchException()
    {
        // Given
        String providerOrderId = "mp-order-123";

        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .id(1)
                .orderId(10)
                .providerOrderId(providerOrderId)
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build();

        MercadoPagoOrderResponseDTO responseDTO = new MercadoPagoOrderResponseDTO(
                providerOrderId, null, MercadoPagoOrderStatus.PROCESSED,
                MercadoPagoOrderStatusDetail.ACCREDITED, "mp-attempt-1",
                new BigDecimal("100.00"), new BigDecimal("1500.00"), "ARS");

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(mercadoPagoRepository.findByProviderOrderId(providerOrderId)).thenReturn(Optional.of(payment));
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri("/v1/orders/{id}", providerOrderId)).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MercadoPagoOrderResponseDTO.class)).thenReturn(responseDTO);

        // When
        assertThrows(PaymentProviderDataMismatchException.class,
                () -> mercadoPagoService.processNotification(providerOrderId));

        // Then
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        verifyNoInteractions(paymentRepository, orderClient);
        verify(mercadoPagoRepository, never()).save(any());
    }

    @Test
    void processNotification_whenPaidAmountDoesNotMatch_thenThrowPaymentProviderDataMismatchException()
    {
        // Given
        String providerOrderId = "mp-order-123";

        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .id(1)
                .orderId(10)
                .providerOrderId(providerOrderId)
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build();

        MercadoPagoOrderResponseDTO responseDTO = new MercadoPagoOrderResponseDTO(
                providerOrderId, null, MercadoPagoOrderStatus.PROCESSED,
                MercadoPagoOrderStatusDetail.ACCREDITED, "mp-attempt-1",
                new BigDecimal("1500.00"), new BigDecimal("100.00"), "ARS");

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(mercadoPagoRepository.findByProviderOrderId(providerOrderId)).thenReturn(Optional.of(payment));
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri("/v1/orders/{id}", providerOrderId)).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MercadoPagoOrderResponseDTO.class)).thenReturn(responseDTO);

        // When
        assertThrows(PaymentProviderDataMismatchException.class,
                () -> mercadoPagoService.processNotification(providerOrderId));

        // Then
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        verifyNoInteractions(paymentRepository, orderClient);
        verify(mercadoPagoRepository, never()).save(any());
    }

    @Test
    void processNotification_whenPaymentSaveFails_thenDoNotCompleteOrder()
    {
        // Given
        String providerOrderId = "mp-order-123";

        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .id(1)
                .orderId(10)
                .providerOrderId(providerOrderId)
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build();

        MercadoPagoOrderResponseDTO responseDTO = new MercadoPagoOrderResponseDTO(
                providerOrderId, null, MercadoPagoOrderStatus.PROCESSED,
                MercadoPagoOrderStatusDetail.ACCREDITED, "mp-attempt-1",
                new BigDecimal("1500.00"), new BigDecimal("1500.00"), "ARS");

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(mercadoPagoRepository.findByProviderOrderId(providerOrderId)).thenReturn(Optional.of(payment));
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri("/v1/orders/{id}", providerOrderId)).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MercadoPagoOrderResponseDTO.class)).thenReturn(responseDTO);
        when(paymentRepository.existsByProviderAndProviderReference(
                PaymentProvider.MERCADO_PAGO, providerOrderId)).thenReturn(false);
        when(paymentRepository.save(any(PaymentEntity.class)))
                .thenThrow(new IllegalStateException("Database unavailable."));

        // When
        assertThrows(IllegalStateException.class,
                () -> mercadoPagoService.processNotification(providerOrderId));

        // Then
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        verify(paymentRepository).save(any(PaymentEntity.class));
        verify(mercadoPagoRepository, never()).save(any());
        verifyNoInteractions(orderClient);
    }

    @Test
    void processNotification_whenOrderCompletionFails_thenCommitPaymentAndPropagateException()
    {
        // Given
        String providerOrderId = "mp-order-123";

        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .id(1)
                .orderId(10)
                .providerOrderId(providerOrderId)
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build();

        MercadoPagoOrderResponseDTO responseDTO = new MercadoPagoOrderResponseDTO(
                providerOrderId, null, MercadoPagoOrderStatus.PROCESSED,
                MercadoPagoOrderStatusDetail.ACCREDITED, "mp-attempt-1",
                new BigDecimal("1500.00"), new BigDecimal("1500.00"), "ARS");

        RestClientException exception = new RestClientException("Order service unavailable.");

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(mercadoPagoRepository.findByProviderOrderId(providerOrderId)).thenReturn(Optional.of(payment));
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri("/v1/orders/{id}", providerOrderId)).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MercadoPagoOrderResponseDTO.class)).thenReturn(responseDTO);
        when(paymentRepository.existsByProviderAndProviderReference(
                PaymentProvider.MERCADO_PAGO, providerOrderId)).thenReturn(false);
        doThrow(exception).when(orderClient).completeOrder(10);

        // When
        RestClientException result = assertThrows(RestClientException.class,
                () -> mercadoPagoService.processNotification(providerOrderId));

        // Then
        assertSame(exception, result);
        assertEquals(PaymentStatus.APPROVED, payment.getStatus());

        InOrder inOrder = inOrder(paymentRepository, mercadoPagoRepository, transactionManager, orderClient);
        inOrder.verify(paymentRepository).save(any(PaymentEntity.class));
        inOrder.verify(mercadoPagoRepository).save(payment);
        inOrder.verify(transactionManager).commit(transactionStatus);
        inOrder.verify(orderClient).completeOrder(10);

        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void processNotification_whenNotifiedAgainAfterOrderCompletionFails_thenCompleteOrderWithoutDuplicatingPayment()
    {
        // Given
        String providerOrderId = "mp-order-123";

        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .id(1)
                .orderId(10)
                .providerOrderId(providerOrderId)
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build();

        MercadoPagoOrderResponseDTO responseDTO = new MercadoPagoOrderResponseDTO(
                providerOrderId, null, MercadoPagoOrderStatus.PROCESSED,
                MercadoPagoOrderStatusDetail.ACCREDITED, "mp-attempt-1",
                new BigDecimal("1500.00"), new BigDecimal("1500.00"), "ARS");

        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(mercadoPagoRepository.findByProviderOrderId(providerOrderId)).thenReturn(Optional.of(payment));
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri("/v1/orders/{id}", providerOrderId)).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MercadoPagoOrderResponseDTO.class)).thenReturn(responseDTO);
        when(paymentRepository.existsByProviderAndProviderReference(
                PaymentProvider.MERCADO_PAGO, providerOrderId)).thenReturn(false, true);
        doThrow(new RestClientException("Order service unavailable."))
                .doNothing().when(orderClient).completeOrder(10);

        // When
        assertThrows(RestClientException.class,
                () -> mercadoPagoService.processNotification(providerOrderId));

        mercadoPagoService.processNotification(providerOrderId);

        // Then
        assertEquals(PaymentStatus.APPROVED, payment.getStatus());

        verify(paymentRepository).save(any(PaymentEntity.class));
        verify(mercadoPagoRepository, times(2)).save(payment);
        verify(transactionManager, times(2)).commit(transactionStatus);
        verify(transactionManager, never()).rollback(any());
        verify(orderClient, times(2)).completeOrder(10);
    }

    @Test
    void createCheckout_whenExistingOrderIsCreated_thenReuseCheckout()
    {
        // Given
        PaymentRequestDTO requestDTO = new PaymentRequestDTO(
                10, new BigDecimal("1500.00"), PaymentProvider.MERCADO_PAGO);

        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .id(1)
                .orderId(10)
                .providerOrderId("mp-order-123")
                .checkoutUrl("https://www.mercadopago.com.ar/old-checkout")
                .idempotencyKey("original-key")
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build();

        MercadoPagoOrderResponseDTO responseDTO = new MercadoPagoOrderResponseDTO(
                "mp-order-123", payment.getCheckoutUrl(), MercadoPagoOrderStatus.CREATED,
                MercadoPagoOrderStatusDetail.CREATED, "mp-attempt-1",
                new BigDecimal("1500.00"), BigDecimal.ZERO, "ARS");

        when(paymentRepository.existsByOrderId(10)).thenReturn(false);
        when(mercadoPagoRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(10, PaymentStatus.PENDING))
                .thenReturn(Optional.of(payment));
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri("/v1/orders/{id}", "mp-order-123")).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MercadoPagoOrderResponseDTO.class)).thenReturn(responseDTO);

        // When
        PaymentLinkResponseDTO result = mercadoPagoService.createCheckout(requestDTO);

        // Then
        assertEquals(payment.getCheckoutUrl(), result.checkoutUrl());
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        verify(restClient).get();
        verify(restClient, never()).post();
        verify(mercadoPagoRepository, never()).save(any());
        verify(mercadoPagoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(orderClient);
    }

    @Test
    void createCheckout_whenExistingOrderIsCanceled_thenCreateNewAttempt()
    {
        // Given
        PaymentRequestDTO requestDTO = new PaymentRequestDTO(
                10, new BigDecimal("1500.00"), PaymentProvider.MERCADO_PAGO);

        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .id(1)
                .orderId(10)
                .providerOrderId("mp-order-123")
                .checkoutUrl("https://www.mercadopago.com.ar/old-checkout")
                .idempotencyKey("original-key")
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build();

        MercadoPagoOrderResponseDTO responseDTO = new MercadoPagoOrderResponseDTO(
                "mp-order-123", payment.getCheckoutUrl(), MercadoPagoOrderStatus.CANCELED,
                MercadoPagoOrderStatusDetail.CANCELED, "mp-attempt-1",
                new BigDecimal("1500.00"), BigDecimal.ZERO, "ARS");

        MercadoPagoPaymentEntity newPayment = MercadoPagoPaymentEntity.builder()
                .id(2)
                .orderId(10)
                .idempotencyKey("new-key")
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build();

        MercadoPagoRequestDTO mercadoPagoRequestDTO = new MercadoPagoRequestDTO(
                "online", "manual", "1500.00", "mp-attempt-2", "Order 10");

        MercadoPagoOrderResponseDTO newOrder = new MercadoPagoOrderResponseDTO(
                "mp-order-456", "https://www.mercadopago.com.ar/new-checkout",
                MercadoPagoOrderStatus.CREATED, MercadoPagoOrderStatusDetail.CREATED,
                "mp-attempt-2", new BigDecimal("1500.00"), BigDecimal.ZERO, "ARS");

        when(paymentRepository.existsByOrderId(10)).thenReturn(false);
        when(mercadoPagoRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(10, PaymentStatus.PENDING))
                .thenReturn(Optional.of(payment));
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri("/v1/orders/{id}", "mp-order-123")).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(mercadoPagoRepository.saveAndFlush(any(MercadoPagoPaymentEntity.class))).thenReturn(newPayment);
        when(restClient.post()).thenReturn(requestBodySpec);
        when(requestBodySpec.uri("/v1/orders")).thenReturn(requestBodySpec);
        when(requestBodySpec.header("X-Idempotency-Key", "new-key")).thenReturn(requestBodySpec);
        when(requestBodySpec.body(mercadoPagoRequestDTO)).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MercadoPagoOrderResponseDTO.class)).thenReturn(responseDTO, newOrder);

        // When
        PaymentLinkResponseDTO result = mercadoPagoService.createCheckout(requestDTO);

        // Then
        assertEquals("https://www.mercadopago.com.ar/new-checkout", result.checkoutUrl());
        assertEquals(PaymentStatus.CANCELLED, payment.getStatus());
        assertEquals(PaymentStatus.PENDING, newPayment.getStatus());
        assertEquals("mp-order-456", newPayment.getProviderOrderId());
        assertEquals(result.checkoutUrl(), newPayment.getCheckoutUrl());

        ArgumentCaptor<MercadoPagoPaymentEntity> captor =
                ArgumentCaptor.forClass(MercadoPagoPaymentEntity.class);
        verify(mercadoPagoRepository).saveAndFlush(captor.capture());

        MercadoPagoPaymentEntity attempt = captor.getValue();
        assertEquals(10, attempt.getOrderId());
        assertEquals(new BigDecimal("1500.00"), attempt.getAmount());
        assertEquals("ARS", attempt.getCurrency());
        assertEquals(PaymentStatus.PENDING, attempt.getStatus());
        assertNotNull(attempt.getIdempotencyKey());
        assertNotEquals(payment.getIdempotencyKey(), attempt.getIdempotencyKey());
        assertNull(attempt.getProviderOrderId());
        assertNull(attempt.getCheckoutUrl());

        verify(mercadoPagoRepository).save(payment);
        verify(mercadoPagoRepository).save(newPayment);
        verify(requestBodySpec).header("X-Idempotency-Key", "new-key");
        verifyNoInteractions(orderClient);
    }

    @Test
    void createCheckout_whenExistingOrderIsProcessing_thenDoNotCreateAnotherCheckout()
    {
        // Given
        PaymentRequestDTO requestDTO = new PaymentRequestDTO(
                10, new BigDecimal("1500.00"), PaymentProvider.MERCADO_PAGO);

        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .id(1)
                .orderId(10)
                .providerOrderId("mp-order-123")
                .checkoutUrl("https://www.mercadopago.com.ar/old-checkout")
                .idempotencyKey("original-key")
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build();

        MercadoPagoOrderResponseDTO responseDTO = new MercadoPagoOrderResponseDTO(
                "mp-order-123", payment.getCheckoutUrl(), MercadoPagoOrderStatus.PROCESSING,
                MercadoPagoOrderStatusDetail.CREATED, "mp-attempt-1",
                new BigDecimal("1500.00"), BigDecimal.ZERO, "ARS");

        when(paymentRepository.existsByOrderId(10)).thenReturn(false);
        when(mercadoPagoRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(10, PaymentStatus.PENDING))
                .thenReturn(Optional.of(payment));
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri("/v1/orders/{id}", "mp-order-123")).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MercadoPagoOrderResponseDTO.class)).thenReturn(responseDTO);

        // When
        assertThrows(InvalidPaymentStateException.class, () -> mercadoPagoService.createCheckout(requestDTO));

        // Then
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        verify(restClient, never()).post();
        verify(mercadoPagoRepository, never()).save(any());
        verify(mercadoPagoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(orderClient);
    }

    @Test
    void createCheckout_whenExistingOrderIsAlreadyProcessed_thenDoNotCreateAnotherCheckout()
    {
        // Given
        PaymentRequestDTO requestDTO = new PaymentRequestDTO(
                10, new BigDecimal("1500.00"), PaymentProvider.MERCADO_PAGO);

        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .id(1)
                .orderId(10)
                .providerOrderId("mp-order-123")
                .checkoutUrl("https://www.mercadopago.com.ar/old-checkout")
                .idempotencyKey("original-key")
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build();

        MercadoPagoOrderResponseDTO responseDTO = new MercadoPagoOrderResponseDTO(
                "mp-order-123", payment.getCheckoutUrl(), MercadoPagoOrderStatus.PROCESSED,
                MercadoPagoOrderStatusDetail.CREATED, "mp-attempt-1",
                new BigDecimal("1500.00"), BigDecimal.ZERO, "ARS");

        when(paymentRepository.existsByOrderId(10)).thenReturn(false);
        when(mercadoPagoRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(10, PaymentStatus.PENDING))
                .thenReturn(Optional.of(payment));
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri("/v1/orders/{id}", "mp-order-123")).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MercadoPagoOrderResponseDTO.class)).thenReturn(responseDTO);

        // When
        assertThrows(InvalidPaymentStateException.class, () -> mercadoPagoService.createCheckout(requestDTO));

        // Then
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        verify(restClient, never()).post();
        verify(mercadoPagoRepository, never()).save(any());
        verify(mercadoPagoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(orderClient);
    }

    @Test
    void createCheckout_whenExistingOrderIsUnknown_thenDoNotCreateAnotherCheckout()
    {
        // Given
        PaymentRequestDTO requestDTO = new PaymentRequestDTO(
                10, new BigDecimal("1500.00"), PaymentProvider.MERCADO_PAGO);

        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .id(1)
                .orderId(10)
                .providerOrderId("mp-order-123")
                .checkoutUrl("https://www.mercadopago.com.ar/old-checkout")
                .idempotencyKey("original-key")
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build();

        MercadoPagoOrderResponseDTO responseDTO = new MercadoPagoOrderResponseDTO(
                "mp-order-123", payment.getCheckoutUrl(), MercadoPagoOrderStatus.UNKNOWN,
                MercadoPagoOrderStatusDetail.CREATED, "mp-attempt-1",
                new BigDecimal("1500.00"), BigDecimal.ZERO, "ARS");

        when(paymentRepository.existsByOrderId(10)).thenReturn(false);
        when(mercadoPagoRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(10, PaymentStatus.PENDING))
                .thenReturn(Optional.of(payment));
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri("/v1/orders/{id}", "mp-order-123")).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MercadoPagoOrderResponseDTO.class)).thenReturn(responseDTO);

        // When
        assertThrows(InvalidPaymentStateException.class, () -> mercadoPagoService.createCheckout(requestDTO));

        // Then
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        verify(restClient, never()).post();
        verify(mercadoPagoRepository, never()).save(any());
        verify(mercadoPagoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(orderClient);
    }

    @Test
    void createCheckout_whenExistingOrderReferenceDoesNotMatch_thenRejectCheckout()
    {
        // Given
        PaymentRequestDTO requestDTO = new PaymentRequestDTO(
                10, new BigDecimal("1500.00"), PaymentProvider.MERCADO_PAGO);

        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .id(1)
                .orderId(10)
                .providerOrderId("mp-order-123")
                .checkoutUrl("https://www.mercadopago.com.ar/old-checkout")
                .idempotencyKey("original-key")
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build();

        MercadoPagoOrderResponseDTO responseDTO = new MercadoPagoOrderResponseDTO(
                "mp-order-123", payment.getCheckoutUrl(), MercadoPagoOrderStatus.CREATED,
                MercadoPagoOrderStatusDetail.CREATED, "mp-attempt-99",
                new BigDecimal("1500.00"), BigDecimal.ZERO, "ARS");

        when(paymentRepository.existsByOrderId(10)).thenReturn(false);
        when(mercadoPagoRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(10, PaymentStatus.PENDING))
                .thenReturn(Optional.of(payment));
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri("/v1/orders/{id}", "mp-order-123")).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MercadoPagoOrderResponseDTO.class)).thenReturn(responseDTO);

        // When
        assertThrows(PaymentProviderDataMismatchException.class, () -> mercadoPagoService.createCheckout(requestDTO));

        // Then
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        verify(restClient, never()).post();
        verify(mercadoPagoRepository, never()).save(any());
        verify(mercadoPagoRepository, never()).saveAndFlush(any());
    }

    @Test
    void createCheckout_whenMercadoPagoLookupFails_thenDoNotReuseOrReplaceCheckout()
    {
        // Given
        PaymentRequestDTO requestDTO = new PaymentRequestDTO(
                10, new BigDecimal("1500.00"), PaymentProvider.MERCADO_PAGO);

        MercadoPagoPaymentEntity payment = MercadoPagoPaymentEntity.builder()
                .id(1)
                .orderId(10)
                .providerOrderId("mp-order-123")
                .checkoutUrl("https://www.mercadopago.com.ar/old-checkout")
                .idempotencyKey("original-key")
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.existsByOrderId(10)).thenReturn(false);
        when(mercadoPagoRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(10, PaymentStatus.PENDING))
                .thenReturn(Optional.of(payment));
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri("/v1/orders/{id}", "mp-order-123")).thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(MercadoPagoOrderResponseDTO.class))
                .thenThrow(new RestClientException("Mercado Pago unavailable."));

        // When
        assertThrows(RestClientException.class, () -> mercadoPagoService.createCheckout(requestDTO));

        // Then
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        verify(restClient, never()).post();
        verify(mercadoPagoRepository, never()).save(any());
        verify(mercadoPagoRepository, never()).saveAndFlush(any());
        verifyNoInteractions(orderClient);
    }
}
