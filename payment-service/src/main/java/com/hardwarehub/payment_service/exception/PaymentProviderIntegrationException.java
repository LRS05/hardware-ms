package com.hardwarehub.payment_service.exception;

public class PaymentProviderIntegrationException extends RuntimeException
{
    public PaymentProviderIntegrationException(String message)
    {
        super(message);
    }
}
