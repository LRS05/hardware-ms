package com.hardwarehub.order_service.config;

import com.hardwarehub.order_service.security.ApiKeyAuthenticationFilter;
import com.hardwarehub.order_service.security.ApiKeyAuthenticationProvider;
import com.hardwarehub.order_service.security.InternalClientProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class AppConfig
{
    @Bean
    @Primary
    public RestClient.Builder defaultRestClientBuilder()
    {
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    public RestClient.Builder restClientBuilder()
    {
        // Maximum of 2 seconds to establish a connection with the server.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        // Maximum of 5 seconds to wait for the HTTP response.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        return RestClient.builder().requestFactory(requestFactory);
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
