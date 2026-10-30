package com.hardwarehub.product_service.security;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ApiKeyAuthenticationProvider implements AuthenticationProvider
{
    private final Map<String, ApiKeyClient> clients;

    // Initialize the configured clients and validate service names and API key uniqueness.
    public ApiKeyAuthenticationProvider(Map<String, ApiKeyClient> clients)
    {
        this.clients = Map.copyOf(clients);

        Set<String> apiKeys = new HashSet<>();

        for (Map.Entry<String, ApiKeyClient> entry : this.clients.entrySet())
        {
            if (entry.getKey().isBlank())
            {
                throw new IllegalArgumentException("Invalid service name");
            }

            if (!apiKeys.add(entry.getValue().apiKey()))
            {
                throw new IllegalArgumentException("Each client must have a different API key.");
            }
        }
    }

    @Override
    public @Nullable Authentication authenticate(@NonNull Authentication authentication) throws AuthenticationException
    {
        if (!supports(authentication.getClass()))
        {
            return null;
        }

        // Retrieve the API key from the authentication token and reject null or blank values.
        Object credentials = authentication.getCredentials();
        if (credentials == null || credentials.toString().isBlank())
        {
            throw new BadCredentialsException("Invalid API Key.");
        }

        byte[] receivedKey = credentials.toString().getBytes(StandardCharsets.UTF_8);

        // Find the client whose API key matches and assign its configured permissions to the authenticated token.
        for (Map.Entry<String, ApiKeyClient> entry : clients.entrySet())
        {
            ApiKeyClient client = entry.getValue();

            byte[] expectedKey = client.apiKey().getBytes(StandardCharsets.UTF_8);

            if (MessageDigest.isEqual(expectedKey, receivedKey))
            {
                List<SimpleGrantedAuthority> authorities = client.permissions().stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

                return ApiKeyAuthenticationToken.buildAuthorizedKey(entry.getKey(), authorities);
            }
        }

        throw new BadCredentialsException("Invalid API Key.");
    }

    @Override
    public boolean supports(@NonNull Class<?> authentication)
    {
        return ApiKeyAuthenticationToken.class.equals(authentication);
    }
}
