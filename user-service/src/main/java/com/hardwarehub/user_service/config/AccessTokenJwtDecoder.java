package com.hardwarehub.user_service.config;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/*
 * This class is used by the OAuth2 Resource Server to validate
 * the token's signature and type.
 */
@Component
public class AccessTokenJwtDecoder implements JwtDecoder
{
    private final JwtDecoder decoder;

    /*
     * The constructor is executed when Spring creates the bean,
     * configuring the secret key and the JWT decoder using HS512.
     */
    public AccessTokenJwtDecoder(@Value("${jwt.secret-key}") String secretKey)
    {
        SecretKey key = new SecretKeySpec(
                secretKey.getBytes(StandardCharsets.UTF_8),
                "HmacSHA512"
        );

        this.decoder = NimbusJwtDecoder
                .withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
    }

    /*
     * When Spring Security needs to validate the token, it calls decode(),
     * which validates the token's signature, expiration, and type (ACCESS).
     */
    @Override
    public @NonNull Jwt decode(@NonNull String token) throws JwtException
    {
        Jwt jwt = decoder.decode(token);

        String type = jwt.getClaimAsString("type");

        if (!"ACCESS".equals(type))
        {
            throw new JwtException("Invalid token type.");
        }

        try
        {
            if (Integer.parseInt(jwt.getSubject()) <= 0)
            {
                throw new BadJwtException("Invalid JWT subject.");
            }
        }
        catch (NumberFormatException e)
        {
            throw new BadJwtException("Invalid JWT subject.", e);
        }

        return jwt;
    }
}
