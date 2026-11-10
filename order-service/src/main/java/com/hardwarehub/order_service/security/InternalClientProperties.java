package com.hardwarehub.order_service.security;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@Getter
@ConfigurationProperties(prefix = "internal")
public class InternalClientProperties
{
    private final Map<String, ApiKeyClient> clients;

    public InternalClientProperties(Map<String, ApiKeyClient> clients)
    {
        this.clients = Map.copyOf(clients);
    }
}
