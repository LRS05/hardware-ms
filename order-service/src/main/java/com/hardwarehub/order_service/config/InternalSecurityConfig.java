package com.hardwarehub.order_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

// TODO: Add API-key authentication before enabling for payment-service.
public class InternalSecurityConfig
{
    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurityFilterChain(HttpSecurity httpSecurity)
    {
        return httpSecurity
                .securityMatcher("/internal", "/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .requestCache(RequestCacheConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.PATCH, "/internal/orders/{orderId}/complete").hasAuthority("ORDER_COMPLETE")
                        .anyRequest().denyAll()
                )
                .build();
    }
}
