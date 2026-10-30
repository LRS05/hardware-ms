package com.hardwarehub.product_service.security;

import org.springframework.security.authentication.BadCredentialsException;

import java.util.List;

public record ApiKeyClient(String apiKey, List<String> permissions)
{
    public ApiKeyClient(String apiKey, List<String> permissions)
    {
        if (apiKey == null || apiKey.isBlank())
        {
            throw new BadCredentialsException("Invalid API Key.");
        }

        this.apiKey = apiKey;
        this.permissions = List.copyOf(permissions);

        for (String permission : this.permissions)
        {
            if (permission.isBlank())
            {
                throw new IllegalArgumentException("Invalid permission.");
            }
        }
    }
}
