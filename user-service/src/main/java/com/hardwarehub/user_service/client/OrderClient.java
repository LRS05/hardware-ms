package com.hardwarehub.user_service.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrderClient
{
    private final RestClient restClient;

    public OrderClient(RestClient.Builder restClient)
    {
        this.restClient = restClient
                .baseUrl("http://order-service/api/v1/orders")
                .build();
    }

    public void createByUserId(int userId)
    {
        restClient.post()
                .uri("/users/{userId}", userId)
                .retrieve()
                .toBodilessEntity();
    }
}
