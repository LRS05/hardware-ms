package com.hardwarehub.payment_service.service;

import com.hardwarehub.payment_service.dto.PaymentLinkResponseDTO;
import com.hardwarehub.payment_service.dto.PaymentRequestDTO;
import com.hardwarehub.payment_service.enums.PaymentProvider;
import com.hardwarehub.payment_service.exception.InvalidPaymentRequestException;
import com.hardwarehub.payment_service.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest
{
    @Mock
    private PaymentServiceProvider provider;

    @Mock
    private PaymentRepository paymentRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp()
    {
        paymentService = new PaymentService(List.of(provider), paymentRepository);
    }

    @Test
    void createCheckout_whenDeadlineHasExpired_thenRejectBeforeCallingProvider()
    {
        // Given
        PaymentRequestDTO requestDTO = new PaymentRequestDTO(
                10, new BigDecimal("1500.00"), PaymentProvider.MERCADO_PAGO,
                Instant.now().minusSeconds(60)
        );

        // When
        assertThrows(InvalidPaymentRequestException.class,
                () -> paymentService.createCheckout(requestDTO));

        // Then
        verifyNoInteractions(provider, paymentRepository);
    }

    @Test
    void createCheckout_whenDeadlineIsInTheFuture_thenDelegateToProvider()
    {
        // Given
        PaymentRequestDTO requestDTO = new PaymentRequestDTO(
                10, new BigDecimal("1500.00"), PaymentProvider.MERCADO_PAGO,
                Instant.now().plusSeconds(900)
        );
        PaymentLinkResponseDTO expected = new PaymentLinkResponseDTO("https://example.com/checkout");

        when(provider.getProvider()).thenReturn(PaymentProvider.MERCADO_PAGO);
        when(provider.createCheckout(requestDTO)).thenReturn(expected);

        // When
        PaymentLinkResponseDTO result = paymentService.createCheckout(requestDTO);

        // Then
        assertEquals(expected, result);
        verify(provider).createCheckout(requestDTO);
        verifyNoInteractions(paymentRepository);
    }
}
