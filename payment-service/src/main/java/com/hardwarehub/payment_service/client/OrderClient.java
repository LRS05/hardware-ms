package com.hardwarehub.payment_service.client;

import com.hardwarehub.payment_service.client.dto.OrderResponseDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrderClient
{
    private final RestClient restClient;

    public OrderClient(
            @LoadBalanced RestClient.Builder restClientBuilder,
            @Value("${internal.auth.api-key}") String apiKey
    )
    {
        this.restClient = restClientBuilder
                .baseUrl("http://order-service/internal/orders")
                .defaultHeader("X-API-Key", apiKey)
                .build();
    }

    public OrderResponseDTO getById(int orderId, int userId)
    {
        return restClient.get()
                .uri("/{orderId}?userId={userId}", orderId, userId)
                .retrieve()
                .body(OrderResponseDTO.class);
    }
}
