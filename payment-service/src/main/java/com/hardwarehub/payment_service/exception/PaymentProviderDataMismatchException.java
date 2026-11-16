package com.hardwarehub.payment_service.exception;

public class PaymentProviderDataMismatchException extends RuntimeException
{
    public PaymentProviderDataMismatchException(String message)
    {
        super(message);
    }
}
