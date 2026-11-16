package com.hardwarehub.payment_service.service;

import com.hardwarehub.payment_service.exception.InvalidPaymentRequestException;
import com.hardwarehub.payment_service.exception.PaymentAlreadyCompletedException;
import com.hardwarehub.payment_service.exception.UnsupportedPaymentProviderException;

import com.hardwarehub.payment_service.dto.PaymentLinkResponseDTO;
import com.hardwarehub.payment_service.dto.PaymentRequestDTO;
import com.hardwarehub.payment_service.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService
{
    private final List<PaymentServiceProvider> providers;
    private final PaymentRepository paymentRepository;

    public PaymentLinkResponseDTO createCheckout(PaymentRequestDTO requestDTO)
    {
        validateOrderIsNotExpired(requestDTO);

        PaymentServiceProvider provider = providers.stream()
                .filter(p -> p.getProvider() == requestDTO.provider())
                .findFirst()
                .orElseThrow(() -> new UnsupportedPaymentProviderException("Unsupported payment provider."));

        return provider.createCheckout(requestDTO);
    }

    public void cancelCheckout(int orderId)
    {
        if (paymentRepository.existsByOrderId(orderId))
        {
            throw new PaymentAlreadyCompletedException("A completed payment cannot be canceled.");
        }

        for (PaymentServiceProvider provider : providers)
        {
            provider.cancelCheckout(orderId);
        }
    }

    private void validateOrderIsNotExpired(PaymentRequestDTO requestDTO)
    {
        if (!requestDTO.expiresAt().isAfter(Instant.now()))
        {
            throw new InvalidPaymentRequestException("The order has expired.");
        }
    }
}
