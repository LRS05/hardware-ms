package com.hardwarehub.payment_service.exception;

public class InvalidPaymentRequestException extends RuntimeException
{
    public InvalidPaymentRequestException(String message)
    {
        super(message);
    }
}
