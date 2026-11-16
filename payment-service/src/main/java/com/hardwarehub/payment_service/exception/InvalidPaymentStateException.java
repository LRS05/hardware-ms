package com.hardwarehub.payment_service.exception;

public class InvalidPaymentStateException extends RuntimeException
{
    public InvalidPaymentStateException(String message)
    {
        super(message);
    }
}
