package com.hardwarehub.payment_service.service;

import com.hardwarehub.payment_service.dto.PaymentLinkResponseDTO;
import com.hardwarehub.payment_service.dto.PaymentRequestDTO;
import com.hardwarehub.payment_service.enums.PaymentProvider;

public interface PaymentServiceProvider
{
    PaymentLinkResponseDTO createCheckout(PaymentRequestDTO requestDTO);

    void cancelCheckout(int orderId);

    PaymentProvider getProvider();
}
