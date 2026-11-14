package com.hardwarehub.payment_service.security;

import com.mercadopago.exceptions.MPInvalidWebhookSignatureException;
import com.mercadopago.webhook.WebhookSignatureValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Slf4j
@Component
public class MercadoPagoWebhookSignatureValidator
{
    private final String webhookSecret;

    public MercadoPagoWebhookSignatureValidator(
            @Value("${mercado-pago.webhook-secret}") String webhookSecret
    )
    {
        if (webhookSecret == null || webhookSecret.isBlank())
        {
            throw new IllegalStateException("Mercado Pago webhook is not configured.");
        }

        this.webhookSecret = webhookSecret;
    }

    public boolean isValid(String dataId, String type, String signature, String requestId)
    {
        if (!"order".equals(type) || dataId == null || dataId.isBlank())
        {
            return false;
        }

        try
        {
            WebhookSignatureValidator.validate(signature, requestId, dataId.toLowerCase(Locale.ROOT), webhookSecret);
            return true;
        }
        catch (MPInvalidWebhookSignatureException e)
        {
            log.warn(
                    "Mercado Pago webhook rejected: reason={}, requestId={}",
                    e.getReason(),
                    requestId
            );
            return false;
        }
    }
}
