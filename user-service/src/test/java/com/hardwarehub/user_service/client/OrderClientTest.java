package com.hardwarehub.user_service.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class OrderClientTest
{
    private OrderClient orderClient;

    private MockRestServiceServer server;

    @BeforeEach
    void setUp()
    {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        orderClient = new OrderClient(builder);
    }

    @Test
    void createByUserId_whenOrderServiceReturnsNoContent_thenSendPostWithUserId()
    {
        int userId = 2;

        server.expect(requestTo("http://order-service/api/v1/orders/users/" + userId))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(""))
                .andRespond(withNoContent());

        orderClient.createByUserId(userId);

        server.verify();
    }

    @Test
    void createByUserId_whenOrderServiceReturnsConflict_thenThrowHttpClientErrorException()
    {
        int userId = 2;

        server.expect(requestTo("http://order-service/api/v1/orders/users/" + userId))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(""))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThrows(HttpClientErrorException.Conflict.class, () -> orderClient.createByUserId(userId));

        server.verify();
    }

    @Test
    void createByUserId_whenOrderServiceReturnsInternalServerError_thenThrowHttpServerErrorException()
    {
        int userId = 2;

        server.expect(requestTo("http://order-service/api/v1/orders/users/" + userId))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(""))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(HttpServerErrorException.InternalServerError.class, () -> orderClient.createByUserId(userId));

        server.verify();
    }
}
