package com.hardwarehub.order_service.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserClient
{
    private final RestClient restClient;

    public UserClient(RestClient.Builder restClient)
    {
        this.restClient = restClient
                .baseUrl("http://user-service/api/v1/users")
                .build();
    }
}
