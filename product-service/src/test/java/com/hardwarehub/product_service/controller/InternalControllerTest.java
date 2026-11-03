package com.hardwarehub.product_service.controller;

import com.hardwarehub.product_service.config.PostgresTestConfig;
import com.hardwarehub.product_service.dto.StockRequestDTO;
import com.hardwarehub.product_service.entity.ProductEntity;
import com.hardwarehub.product_service.enums.ApiError;
import com.hardwarehub.product_service.enums.ProductStatus;
import com.hardwarehub.product_service.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@Sql(statements = "DELETE FROM products")
@Sql("/data.sql")
public class InternalControllerTest
{
    @Autowired
    private ProductRepository productRepository;

    @LocalServerPort
    private int port;

    @Value("${internal.clients.order-service.api-key}")
    private String apiKey;

    private RestTestClient restTestClient;

    @BeforeEach
    void setUp()
    {
        restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/internal/products")
                .defaultHeader("X-API-Key", apiKey)
                .build();
    }

    @Test
    void decreaseStockById_whenEnoughStockExists_thenReturnNoContent()
    {
        int productId = 1;
        StockRequestDTO requestDTO = new StockRequestDTO(10);

        restTestClient.patch()
                .uri("/{id}/decrease", productId)
                .body(requestDTO)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void decreaseStockById_whenStockIsInsufficient_thenReturnConflict()
    {
        int productId = 1;
        StockRequestDTO requestDTO = new StockRequestDTO(51);

        restTestClient.patch()
                .uri("/{id}/decrease", productId)
                .body(requestDTO)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.INSUFFICIENT_STOCK.name())
                .jsonPath("$.message").isEqualTo("There's not enough stock of this product.");
    }

    @Test
    void increaseStockById_whenRequestIsValid_thenReturnNoContent()
    {
        int productId = 1;
        StockRequestDTO requestDTO = new StockRequestDTO(10);

        restTestClient.patch()
                .uri("/{id}/increase", productId)
                .body(requestDTO)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void increaseStockById_whenProductIsInactive_thenIncreaseStockAndKeepInactive()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.INACTIVE);
        product.setStock(0);
        productRepository.saveAndFlush(product);

        StockRequestDTO requestDTO = new StockRequestDTO(10);

        restTestClient.patch()
                .uri("/{id}/increase", productId)
                .body(requestDTO)
                .exchange()
                .expectStatus().isNoContent();

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(10, updatedProduct.getStock());
        assertEquals(ProductStatus.INACTIVE, updatedProduct.getStatus());
    }

    @Test
    void increaseStockById_whenProductIsOutOfStock_thenIncreaseStockAndActivate()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.OUT_OF_STOCK);
        product.setStock(0);
        productRepository.saveAndFlush(product);

        StockRequestDTO requestDTO = new StockRequestDTO(10);

        restTestClient.patch()
                .uri("/{id}/increase", productId)
                .body(requestDTO)
                .exchange()
                .expectStatus().isNoContent();

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(10, updatedProduct.getStock());
        assertEquals(ProductStatus.ACTIVE, updatedProduct.getStatus());
    }

    @Test
    void decreaseStockById_whenStockReachesZero_thenMarkProductOutOfStock()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.ACTIVE);
        product.setStock(50);
        productRepository.saveAndFlush(product);

        StockRequestDTO requestDTO = new StockRequestDTO(50);

        restTestClient.patch()
                .uri("/{id}/decrease", productId)
                .body(requestDTO)
                .exchange()
                .expectStatus().isNoContent();

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(0, updatedProduct.getStock());
        assertEquals(ProductStatus.OUT_OF_STOCK, updatedProduct.getStatus());
    }
}
