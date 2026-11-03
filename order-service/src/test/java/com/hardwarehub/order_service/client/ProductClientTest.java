package com.hardwarehub.order_service.client;

import com.hardwarehub.order_service.client.dto.ProductResponseDTO;
import com.hardwarehub.order_service.exception.ProductIntegrationException;
import com.hardwarehub.order_service.exception.ProductNotFoundException;
import com.hardwarehub.order_service.exception.ProductOutOfStockException;
import com.hardwarehub.order_service.exception.ProductTimeoutException;
import com.hardwarehub.order_service.exception.ProductUnavailableException;
import com.hardwarehub.order_service.factory.ProductFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@SpringJUnitConfig
@TestPropertySource(locations = "classpath:application-test.properties")
public class ProductClientTest
{
    @Value("${internal.auth.api-key}")
    private String apiKey;

    private ProductClient productClient;

    private MockRestServiceServer server;

    @BeforeEach
    void setUp()
    {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        productClient = new ProductClient(builder, apiKey);
    }

    @Test
    void getById_whenProductServiceReturnsProduct_thenReturnProduct()
    {
        int productId = 1;
        ProductResponseDTO expectedProduct = ProductFactory.availableGraphicCardDTO();

        String responseBody = JsonMapper.builder().build().writeValueAsString(expectedProduct);

        server.expect(requestTo("http://product-service/internal/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().string(""))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        ProductResponseDTO product = productClient.getById(productId);

        assertEquals(expectedProduct.id(), product.id());
        assertEquals(expectedProduct.status(), product.status());
        assertEquals(expectedProduct.category(), product.category());
        assertEquals(expectedProduct.sku(), product.sku());
        assertEquals(expectedProduct.name(), product.name());
        assertEquals(expectedProduct.description(), product.description());
        assertEquals(expectedProduct.price(), product.price());
        assertEquals(expectedProduct.stock(), product.stock());
        assertEquals(expectedProduct.createdAt(), product.createdAt());
        assertEquals(expectedProduct.updatedAt(), product.updatedAt());

        server.verify();
    }

    @Test
    void getById_whenProductServiceReturnsNotFound_thenThrowProductNotFoundException()
    {
        int productId = 1;

        server.expect(requestTo("http://product-service/internal/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().string(""))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(ProductNotFoundException.class, () -> productClient.getById(productId));

        server.verify();
    }

    @Test
    void getById_whenProductServiceReturnsConflict_thenThrowProductOutOfStockException()
    {
        int productId = 1;

        server.expect(requestTo("http://product-service/internal/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().string(""))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThrows(ProductOutOfStockException.class, () -> productClient.getById(productId));

        server.verify();
    }

    @Test
    void getById_whenProductServiceReturnsInternalServerError_thenThrowProductIntegrationException()
    {
        int productId = 1;

        server.expect(requestTo("http://product-service/internal/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().string(""))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(ProductIntegrationException.class, () -> productClient.getById(productId));

        server.verify();
    }

    @Test
    void decreaseStockById_whenProductServiceReturnsNoContent_thenSendPatchWithQuantity()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/decrease"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withNoContent());

        productClient.decreaseStockById(productId, quantity);

        server.verify();
    }

    @Test
    void decreaseStockById_whenProductServiceReturnsNotFound_thenThrowProductNotFoundException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/decrease"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(ProductNotFoundException.class, () -> productClient.decreaseStockById(productId, quantity));

        server.verify();
    }

    @Test
    void decreaseStockById_whenProductServiceReturnsConflict_thenThrowProductOutOfStockException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/decrease"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThrows(ProductOutOfStockException.class, () -> productClient.decreaseStockById(productId, quantity));

        server.verify();
    }

    @Test
    void decreaseStockById_whenProductServiceReturnsInternalServerError_thenThrowProductIntegrationException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/decrease"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(ProductIntegrationException.class, () -> productClient.decreaseStockById(productId, quantity));

        server.verify();
    }

    @Test
    void increaseStockById_whenProductServiceReturnsNoContent_thenSendPatchWithQuantity()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/increase"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withNoContent());

        productClient.increaseStockById(productId, quantity);

        server.verify();
    }

    @Test
    void increaseStockById_whenProductServiceReturnsNotFound_thenThrowProductNotFoundException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/increase"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(ProductNotFoundException.class, () -> productClient.increaseStockById(productId, quantity));

        server.verify();
    }

    @Test
    void increaseStockById_whenProductServiceReturnsConflict_thenThrowProductOutOfStockException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/increase"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThrows(ProductOutOfStockException.class, () -> productClient.increaseStockById(productId, quantity));

        server.verify();
    }

    @Test
    void increaseStockById_whenProductServiceReturnsInternalServerError_thenThrowProductIntegrationException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/increase"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(ProductIntegrationException.class, () -> productClient.increaseStockById(productId, quantity));

        server.verify();
    }

    @Test
    void getById_whenProductServiceReturnsUnauthorized_thenThrowProductIntegrationException()
    {
        int productId = 1;

        server.expect(requestTo("http://product-service/internal/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().string(""))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(ProductIntegrationException.class, () -> productClient.getById(productId));

        server.verify();
    }

    @Test
    void getById_whenProductServiceReturnsForbidden_thenThrowProductIntegrationException()
    {
        int productId = 1;

        server.expect(requestTo("http://product-service/internal/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().string(""))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThrows(ProductIntegrationException.class, () -> productClient.getById(productId));

        server.verify();
    }

    @Test
    void decreaseStockById_whenProductServiceReturnsUnauthorized_thenThrowProductIntegrationException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/decrease"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(ProductIntegrationException.class, () -> productClient.decreaseStockById(productId, quantity));

        server.verify();
    }

    @Test
    void decreaseStockById_whenProductServiceReturnsForbidden_thenThrowProductIntegrationException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/decrease"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThrows(ProductIntegrationException.class, () -> productClient.decreaseStockById(productId, quantity));

        server.verify();
    }

    @Test
    void increaseStockById_whenProductServiceReturnsUnauthorized_thenThrowProductIntegrationException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/increase"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(ProductIntegrationException.class, () -> productClient.increaseStockById(productId, quantity));

        server.verify();
    }

    @Test
    void increaseStockById_whenProductServiceReturnsForbidden_thenThrowProductIntegrationException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/increase"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThrows(ProductIntegrationException.class, () -> productClient.increaseStockById(productId, quantity));

        server.verify();
    }

    @Test
    void getById_whenProductServiceReturnsBadRequest_thenThrowProductIntegrationException()
    {
        int productId = 1;

        server.expect(requestTo("http://product-service/internal/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().string(""))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThrows(ProductIntegrationException.class, () -> productClient.getById(productId));

        server.verify();
    }

    @Test
    void getById_whenProductServiceRefusesConnection_thenThrowProductUnavailableException()
    {
        int productId = 1;

        server.expect(requestTo("http://product-service/internal/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().string(""))
                .andRespond(withException(new ConnectException("Connection refused")));

        assertThrows(ProductUnavailableException.class, () -> productClient.getById(productId));

        server.verify();
    }

    @Test
    void getById_whenProductServiceReadTimesOut_thenThrowProductTimeoutException()
    {
        int productId = 1;

        server.expect(requestTo("http://product-service/internal/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().string(""))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThrows(ProductTimeoutException.class, () -> productClient.getById(productId));

        server.verify();
    }

    @Test
    void getById_whenProductServiceConnectionTimesOut_thenThrowProductTimeoutException()
    {
        int productId = 1;

        server.expect(requestTo("http://product-service/internal/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().string(""))
                .andRespond(withException(new HttpConnectTimeoutException("Connection timed out")));

        assertThrows(ProductTimeoutException.class, () -> productClient.getById(productId));

        server.verify();
    }

    @Test
    void getById_whenProductServiceThrowsWrappedTimeout_thenThrowProductTimeoutException()
    {
        int productId = 1;

        server.expect(requestTo("http://product-service/internal/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().string(""))
                .andRespond(withException(new IOException("Request failed", new SocketTimeoutException("Read timed out"))));

        assertThrows(ProductTimeoutException.class, () -> productClient.getById(productId));

        server.verify();
    }

    @Test
    void getById_whenProductServiceReturnsInvalidJson_thenThrowProductIntegrationException()
    {
        int productId = 1;

        server.expect(requestTo("http://product-service/internal/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().string(""))
                .andRespond(withSuccess("invalid-json", MediaType.APPLICATION_JSON));

        assertThrows(ProductIntegrationException.class, () -> productClient.getById(productId));

        server.verify();
    }

    @Test
    void getById_whenLoadBalancerHasNoInstances_thenThrowProductUnavailableException()
    {
        int productId = 1;

        server.expect(requestTo("http://product-service/internal/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().string(""))
                .andRespond(request ->
                {
                    throw new IllegalStateException("No instances available for product-service");
                });

        assertThrows(ProductUnavailableException.class, () -> productClient.getById(productId));

        server.verify();
    }

    @Test
    void getById_whenLoadBalancerThrowsUnrelatedIllegalState_thenThrowIllegalStateException()
    {
        int productId = 1;

        server.expect(requestTo("http://product-service/internal/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().string(""))
                .andRespond(request ->
                {
                    throw new IllegalStateException("Unexpected client state");
                });

        assertThrows(IllegalStateException.class, () -> productClient.getById(productId));

        server.verify();
    }

    @Test
    void decreaseStockById_whenProductServiceReturnsBadRequest_thenThrowProductIntegrationException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/decrease"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThrows(ProductIntegrationException.class, () -> productClient.decreaseStockById(productId, quantity));

        server.verify();
    }

    @Test
    void decreaseStockById_whenProductServiceRefusesConnection_thenThrowProductUnavailableException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/decrease"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withException(new ConnectException("Connection refused")));

        assertThrows(ProductUnavailableException.class, () -> productClient.decreaseStockById(productId, quantity));

        server.verify();
    }

    @Test
    void decreaseStockById_whenProductServiceReadTimesOut_thenThrowProductTimeoutException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/decrease"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThrows(ProductTimeoutException.class, () -> productClient.decreaseStockById(productId, quantity));

        server.verify();
    }

    @Test
    void increaseStockById_whenProductServiceReturnsBadRequest_thenThrowProductIntegrationException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/increase"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThrows(ProductIntegrationException.class, () -> productClient.increaseStockById(productId, quantity));

        server.verify();
    }

    @Test
    void increaseStockById_whenProductServiceRefusesConnection_thenThrowProductUnavailableException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/increase"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withException(new ConnectException("Connection refused")));

        assertThrows(ProductUnavailableException.class, () -> productClient.increaseStockById(productId, quantity));

        server.verify();
    }

    @Test
    void increaseStockById_whenProductServiceReadTimesOut_thenThrowProductTimeoutException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/increase"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThrows(ProductTimeoutException.class, () -> productClient.increaseStockById(productId, quantity));

        server.verify();
    }
}
