package com.hardwarehub.order_service.client;

import com.hardwarehub.order_service.dto.PaymentLinkResponseDTO;
import com.hardwarehub.order_service.dto.PaymentRequestDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class PaymentClient
{
    private final RestClient restClient;

    public PaymentClient(
            @LoadBalanced RestClient.Builder restClientBuilder,
            @Value("${internal.auth.api-key}") String apiKey
    )
    {
        // Maximum time allowed to establish a connection.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        // Maximum time allowed to wait for the service response.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));

        this.restClient = restClientBuilder.clone()
                .requestFactory(requestFactory)
                .baseUrl("http://payment-service/internal/payments")
                .defaultHeader("X-API-Key", apiKey)
                .build();
    }

    public PaymentLinkResponseDTO createCheckout(PaymentRequestDTO requestDTO)
    {
        PaymentLinkResponseDTO responseDTO = restClient.post()
                .uri("/checkout")
                .body(requestDTO)
                .retrieve()
                .body(PaymentLinkResponseDTO.class);

        if (responseDTO == null || responseDTO.checkoutUrl() == null || responseDTO.checkoutUrl().isBlank())
        {
            throw new IllegalStateException("Payment service returned no checkout URL.");
        }

        return responseDTO;
    }

    public void cancelCheckout(int orderId)
    {
        restClient.post()
                .uri("/{orderId}/cancel", orderId)
                .retrieve()
                .toBodilessEntity();
    }
}
