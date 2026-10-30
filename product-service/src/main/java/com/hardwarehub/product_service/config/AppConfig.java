package com.hardwarehub.product_service.config;

import com.hardwarehub.product_service.security.ApiKeyAuthenticationFilter;
import com.hardwarehub.product_service.security.ApiKeyAuthenticationProvider;
import com.hardwarehub.product_service.security.InternalClientProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig
{
    @Bean
    @LoadBalanced
    public RestClient.Builder restClientBuilder()
    {
        return RestClient.builder();
    }

    @Bean
    public ApiKeyAuthenticationProvider apiKeyAuthenticationProvider(InternalClientProperties properties)
    {
        return new ApiKeyAuthenticationProvider(properties.getClients());
    }

    @Bean
    public AuthenticationManager apiKeyAuthenticationManager(ApiKeyAuthenticationProvider provider)
    {
        return new ProviderManager(provider);
    }

    @Bean
    public FilterRegistrationBean<ApiKeyAuthenticationFilter> apiKeyFilterRegistration(ApiKeyAuthenticationFilter filter)
    {
        FilterRegistrationBean<ApiKeyAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);
        return registration;
    }
}
