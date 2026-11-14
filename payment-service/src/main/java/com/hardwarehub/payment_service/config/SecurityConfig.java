package com.hardwarehub.payment_service.config;

import com.hardwarehub.payment_service.security.ApiKeyAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig
{
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity)
    {
        return httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
                .build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurityFilterChain(
            HttpSecurity httpSecurity,
            ApiKeyAuthenticationFilter filter
    )
    {
        return httpSecurity
                .securityMatcher("/internal", "/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .requestCache(RequestCacheConfigurer::disable)

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(HttpMethod.POST, "/internal/payments/checkout").hasAuthority("PAYMENT_CREATE")
                        .requestMatchers(HttpMethod.POST, "/internal/payments/{orderId}/cancel").hasAuthority("PAYMENT_CANCEL")

                        .anyRequest().denyAll()
                )
                .exceptionHandling(e -> e
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.setStatus(HttpStatus.FORBIDDEN.value()))
                )
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webhookSecurityFilterChain(HttpSecurity httpSecurity)
    {
        return httpSecurity
                .securityMatcher("/api/v1/payments/webhooks/mercado-pago")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/webhooks/mercado-pago").permitAll()
                        .anyRequest().denyAll()
                )
                .build();
    }
}
