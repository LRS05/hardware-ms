package com.hardwarehub.order_service.client;

import com.hardwarehub.order_service.client.dto.ProductResponseDTO;
import com.hardwarehub.order_service.exception.ProductNotFoundException;
import com.hardwarehub.order_service.exception.ProductOutOfStockException;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

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

        ProductNotFoundException exception = assertThrows(ProductNotFoundException.class, () -> productClient.getById(productId));

        assertEquals("Product not found.", exception.getMessage());

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

        ProductOutOfStockException exception = assertThrows(ProductOutOfStockException.class, () -> productClient.getById(productId));

        assertEquals("The product is out of stock.", exception.getMessage());

        server.verify();
    }

    @Test
    void getById_whenProductServiceReturnsInternalServerError_thenThrowHttpServerErrorException()
    {
        int productId = 1;

        server.expect(requestTo("http://product-service/internal/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().string(""))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(HttpServerErrorException.InternalServerError.class, () -> productClient.getById(productId));

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

        ProductNotFoundException exception = assertThrows(ProductNotFoundException.class, () -> productClient.decreaseStockById(productId, quantity));

        assertEquals("Product not found.", exception.getMessage());

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

        ProductOutOfStockException exception = assertThrows(ProductOutOfStockException.class, () -> productClient.decreaseStockById(productId, quantity));

        assertEquals("This product is out of stock.", exception.getMessage());

        server.verify();
    }

    @Test
    void decreaseStockById_whenProductServiceReturnsInternalServerError_thenThrowHttpServerErrorException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/decrease"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(HttpServerErrorException.InternalServerError.class, () -> productClient.decreaseStockById(productId, quantity));

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

        ProductNotFoundException exception = assertThrows(ProductNotFoundException.class, () -> productClient.increaseStockById(productId, quantity));

        assertEquals("Product not found.", exception.getMessage());

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

        ProductOutOfStockException exception = assertThrows(ProductOutOfStockException.class, () -> productClient.increaseStockById(productId, quantity));

        assertEquals("This product is out of stock.", exception.getMessage());

        server.verify();
    }

    @Test
    void increaseStockById_whenProductServiceReturnsInternalServerError_thenThrowHttpServerErrorException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/increase"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(HttpServerErrorException.InternalServerError.class, () -> productClient.increaseStockById(productId, quantity));

        server.verify();
    }

    @Test
    void getById_whenProductServiceReturnsUnauthorized_thenThrowUnauthorizedException()
    {
        int productId = 1;

        server.expect(requestTo("http://product-service/internal/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().string(""))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(HttpClientErrorException.Unauthorized.class, () -> productClient.getById(productId));

        server.verify();
    }

    @Test
    void getById_whenProductServiceReturnsForbidden_thenThrowForbiddenException()
    {
        int productId = 1;

        server.expect(requestTo("http://product-service/internal/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().string(""))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThrows(HttpClientErrorException.Forbidden.class, () -> productClient.getById(productId));

        server.verify();
    }

    @Test
    void decreaseStockById_whenProductServiceReturnsUnauthorized_thenThrowUnauthorizedException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/decrease"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(HttpClientErrorException.Unauthorized.class, () -> productClient.decreaseStockById(productId, quantity));

        server.verify();
    }

    @Test
    void decreaseStockById_whenProductServiceReturnsForbidden_thenThrowForbiddenException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/decrease"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThrows(HttpClientErrorException.Forbidden.class, () -> productClient.decreaseStockById(productId, quantity));

        server.verify();
    }

    @Test
    void increaseStockById_whenProductServiceReturnsUnauthorized_thenThrowUnauthorizedException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/increase"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(HttpClientErrorException.Unauthorized.class, () -> productClient.increaseStockById(productId, quantity));

        server.verify();
    }

    @Test
    void increaseStockById_whenProductServiceReturnsForbidden_thenThrowForbiddenException()
    {
        int productId = 1;
        int quantity = 2;

        server.expect(requestTo("http://product-service/internal/products/" + productId + "/increase"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("X-API-Key", apiKey))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThrows(HttpClientErrorException.Forbidden.class, () -> productClient.increaseStockById(productId, quantity));

        server.verify();
    }
}
