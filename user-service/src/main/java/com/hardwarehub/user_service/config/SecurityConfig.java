package com.hardwarehub.user_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/*
 * Enables method security to restrict specific
 * AdminController methods using @PreAuthorize.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig
{
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity httpSecurity,
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter
    )
    {
        return httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh"
                        ).permitAll()

                        .requestMatchers("/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/users/**").hasRole("CUSTOMER")

                        // Both roles can access, but some operations are restricted to ADMIN.
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "STAFF")

                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2

                        // Validates the token's signature and reads the roles.
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)
                                .decoder(jwtDecoder)
                        )
                )
                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter()
    {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        return converter;
    }
}
