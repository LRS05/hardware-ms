package com.hardwarehub.order_service.config;

import com.hardwarehub.order_service.security.ApiKeyAuthenticationFilter;
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
public class InternalSecurityConfig
{
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
                        .requestMatchers(HttpMethod.GET, "/internal/orders/{orderId}")
                        .hasAuthority("ORDER_READ")

                        .requestMatchers(HttpMethod.PATCH, "/internal/orders/{orderId}/complete")
                        .hasAuthority("ORDER_COMPLETE")

                        .anyRequest().denyAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .accessDeniedHandler((request, response, exception) ->
                                response.setStatus(HttpStatus.FORBIDDEN.value()))
                )
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
