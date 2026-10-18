package com.hardwarehub.api_gateway.security;

import com.hardwarehub.api_gateway.jwt.AccessTokenJwtDecoder;
import com.hardwarehub.api_gateway.jwt.JwtPropagationFilterSupplier;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Arrays;

@Configuration
public class SecurityConfig
{
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            BearerTokenResolver bearerTokenResolver,
            AccessTokenJwtDecoder jwtDecoder
    ) throws Exception
    {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2

                                // Extract the token from the cookie
                                .bearerTokenResolver(bearerTokenResolver)

                                // Validate the signature and decode the JWT
                                .jwt(jwt -> jwt.decoder(jwtDecoder))
                )
                .build();
    }

    @Bean
    public BearerTokenResolver bearerTokenResolver()
    {
        return request ->
        {
            if (request.getCookies() == null) return null;

            return Arrays.stream(request.getCookies())
                    .filter(c -> c.getName().equals("access-token"))
                    .findFirst()
                    .map(Cookie::getValue)
                    .orElse(null);
        };
    }

    @Bean
    public JwtPropagationFilterSupplier jwtPropagationFilterSupplier()
    {
        return new JwtPropagationFilterSupplier();
    }
}
