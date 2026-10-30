package com.hardwarehub.order_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccessTokenJwtDecoderTest
{
    private static final String SECRET_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final AccessTokenJwtDecoder decoder = new AccessTokenJwtDecoder(SECRET_KEY);

    @Test
    void decode_whenSubjectIsUserId_thenReturnJwt()
    {
        assertEquals("2", decoder.decode(buildToken("2")).getSubject());
    }

    @Test
    void decode_whenSubjectIsUsername_thenThrowBadJwtException()
    {
        String token = buildToken("Marceloviviani");

        assertThrows(BadJwtException.class, () -> decoder.decode(token));
    }

    @Test
    void decode_whenSubjectIsMissing_thenThrowBadJwtException()
    {
        String token = buildToken(null);

        assertThrows(BadJwtException.class, () -> decoder.decode(token));
    }

    @Test
    void decode_whenSubjectIsOutOfRange_thenThrowBadJwtException()
    {
        String token = buildToken("2147483648");

        assertThrows(BadJwtException.class, () -> decoder.decode(token));
    }

    @Test
    void decode_whenSubjectIsZero_thenThrowBadJwtException()
    {
        String token = buildToken("0");

        assertThrows(BadJwtException.class, () -> decoder.decode(token));
    }

    @Test
    void decode_whenSubjectIsNegative_thenThrowBadJwtException()
    {
        String token = buildToken("-1");

        assertThrows(BadJwtException.class, () -> decoder.decode(token));
    }

    private String buildToken(String subject)
    {
        JwtEncoder encoder = NimbusJwtEncoder.withSecretKey(
                        new SecretKeySpec(SECRET_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA512")
                )
                .algorithm(MacAlgorithm.HS512)
                .build();

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("type", "ACCESS")
                .claim("role", "CUSTOMER");

        if (subject != null)
        {
            claimsBuilder.subject(subject);
        }

        JwtClaimsSet claims = claimsBuilder.build();

        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS512).build(), claims
        )).getTokenValue();
    }
}
