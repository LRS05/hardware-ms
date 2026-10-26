package com.hardwarehub.order_service.client;

import com.hardwarehub.order_service.client.dto.ProductResponseDTO;
import com.hardwarehub.order_service.exception.ProductNotFoundException;
import com.hardwarehub.order_service.exception.ProductOutOfStockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

public class ProductClientTest
{
    private ProductClient productClient;

    private MockRestServiceServer server;

    @BeforeEach
    void setUp()
    {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        productClient = new ProductClient(builder);
    }

    @Test
    void getById_whenProductServiceReturnsProduct_thenReturnProduct()
    {
        int productId = 1;
        String responseBody = """
                {
                    "id": 1,
                    "status": "AVAILABLE",
                    "category": "GRAPHIC_CARD",
                    "sku": "GPU-1080-TI",
                    "name": "NVIDIA GTX 1080-TI",
                    "description": "Graphics card",
                    "price": 2000.00,
                    "stock": 10,
                    "created_at": "2026-10-23T10:05:00",
                    "updated_at": "2026-10-24T11:30:00"
                }
                """;

        server.expect(requestTo("http://product-service/api/v1/products/" + productId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(content().string(""))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        ProductResponseDTO product = productClient.getById(productId);

        assertEquals(productId, product.id());
        assertEquals("AVAILABLE", product.status());
        assertEquals("GRAPHIC_CARD", product.category());
        assertEquals("GPU-1080-TI", product.sku());
        assertEquals("NVIDIA GTX 1080-TI", product.name());
        assertEquals("Graphics card", product.description());
        assertEquals(new BigDecimal("2000.00"), product.price());
        assertEquals(10, product.stock());
        assertEquals(LocalDateTime.of(2026, 10, 23, 10, 5), product.createdAt());
        assertEquals(LocalDateTime.of(2026, 10, 24, 11, 30), product.updatedAt());

        server.verify();
    }

    @Test
    void getById_whenProductServiceReturnsNotFound_thenThrowProductNotFoundException()
    {
        int productId = 1;

        server.expect(requestTo("http://product-service/api/v1/products/" + productId))
                .andExpect(method(HttpMethod.GET))
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

        server.expect(requestTo("http://product-service/api/v1/products/" + productId))
                .andExpect(method(HttpMethod.GET))
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

        server.expect(requestTo("http://product-service/api/v1/products/" + productId))
                .andExpect(method(HttpMethod.GET))
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

        server.expect(requestTo("http://product-service/api/v1/products/" + productId + "/decrease"))
                .andExpect(method(HttpMethod.PATCH))
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

        server.expect(requestTo("http://product-service/api/v1/products/" + productId + "/decrease"))
                .andExpect(method(HttpMethod.PATCH))
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

        server.expect(requestTo("http://product-service/api/v1/products/" + productId + "/decrease"))
                .andExpect(method(HttpMethod.PATCH))
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

        server.expect(requestTo("http://product-service/api/v1/products/" + productId + "/decrease"))
                .andExpect(method(HttpMethod.PATCH))
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

        server.expect(requestTo("http://product-service/api/v1/products/" + productId + "/increase"))
                .andExpect(method(HttpMethod.PATCH))
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

        server.expect(requestTo("http://product-service/api/v1/products/" + productId + "/increase"))
                .andExpect(method(HttpMethod.PATCH))
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

        server.expect(requestTo("http://product-service/api/v1/products/" + productId + "/increase"))
                .andExpect(method(HttpMethod.PATCH))
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

        server.expect(requestTo("http://product-service/api/v1/products/" + productId + "/increase"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"stock\":" + quantity + "}"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(HttpServerErrorException.InternalServerError.class, () -> productClient.increaseStockById(productId, quantity));

        server.verify();
    }
}
