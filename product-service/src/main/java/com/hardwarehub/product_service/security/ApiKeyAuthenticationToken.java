package com.hardwarehub.product_service.security;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken
{
    private final String serviceName;
    private String apiKey;

    private ApiKeyAuthenticationToken(String apiKey)
    {
        super(List.of());

        if (apiKey == null || apiKey.isBlank())
        {
            throw new BadCredentialsException("Invalid API Key.");
        }

        this.serviceName = null;
        this.apiKey = apiKey;
    }

    private ApiKeyAuthenticationToken(String serviceName, Collection<? extends GrantedAuthority> authorities)
    {
        super(authorities);

        if (serviceName == null || serviceName.isBlank())
        {
            throw new BadCredentialsException("Invalid service name.");
        }

        this.serviceName = serviceName;
        super.setAuthenticated(true);
    }

    // Construye la key sin autorización desde el primer constructor
    public static ApiKeyAuthenticationToken buildUnauthorizedKey(String apiKey)
    {
        return new ApiKeyAuthenticationToken(apiKey);
    }

    // Construye la key autorizada desde el segundo constructor
    public static ApiKeyAuthenticationToken buildAuthorizedKey(String serviceName, Collection<? extends GrantedAuthority> authorities)
    {
        return new ApiKeyAuthenticationToken(serviceName, authorities);
    }

    @Override
    public void setAuthenticated(boolean authenticated)
    {
        if (authenticated)
        {
            throw new IllegalArgumentException("Use the authenticated factory method to create an authenticated token.");
        }

        super.setAuthenticated(false);
    }

    @Override
    public @Nullable Object getCredentials() {
        return this.apiKey;
    }

    @Override
    public @Nullable Object getPrincipal() {
        return this.serviceName;
    }

    @Override
    public void eraseCredentials()
    {
        super.eraseCredentials();
        apiKey = null;
    }
}
