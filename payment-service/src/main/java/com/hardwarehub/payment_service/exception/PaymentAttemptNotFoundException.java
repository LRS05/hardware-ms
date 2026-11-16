package com.hardwarehub.payment_service.exception;

public class PaymentAttemptNotFoundException extends RuntimeException
{
    public PaymentAttemptNotFoundException(String message)
    {
        super(message);
    }
}
