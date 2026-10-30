package com.hardwarehub.product_service.config;

import com.hardwarehub.product_service.security.ApiKeyAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class InternalSecurityConfig
{
    @Bean
    @Order(1)
    public SecurityFilterChain internalSecurityFilterChain(
            HttpSecurity httpSecurity,
            ApiKeyAuthenticationFilter filter)
    {
        return httpSecurity
                .securityMatcher("/internal", "/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .requestCache(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(HttpMethod.GET, "/internal/products/{id}").hasAuthority("PRODUCT_READ")
                        .requestMatchers(HttpMethod.PATCH, "/internal/products/{id}/decrease").hasAuthority("STOCK_DECREASE")
                        .requestMatchers(HttpMethod.PATCH, "/internal/products/{id}/increase").hasAuthority("STOCK_INCREASE")

                        .anyRequest().denyAll()
                )
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
