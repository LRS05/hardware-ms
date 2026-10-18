package com.hardwarehub.user_service.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrderClient
{
    private RestClient restClient;

    public OrderClient(RestClient.Builder restClient)
    {
        this.restClient = restClient
                .baseUrl("http://order-service/api/orders")
                .build();
    }
}
