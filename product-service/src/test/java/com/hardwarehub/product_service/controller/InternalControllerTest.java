package com.hardwarehub.product_service.controller;

import com.hardwarehub.product_service.config.PostgresTestConfig;
import com.hardwarehub.product_service.dto.StockRequestDTO;
import com.hardwarehub.product_service.dto.ItemReservationDTO;
import com.hardwarehub.product_service.dto.StockReservationRequestDTO;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@Sql(statements = "DELETE FROM products")
@Sql("/data.sql")
@SpringBootTest(properties = "spring.config.location=classpath:/application-test.properties", webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
    void reserveStock_whenAllProductsHaveStock_thenDecreaseEntireBatch()
    {
        StockReservationRequestDTO requestDTO = new StockReservationRequestDTO(List.of(
                new ItemReservationDTO(2, 3),
                new ItemReservationDTO(1, 50)
        ));

        restTestClient.patch()
                .uri("/reserve")
                .body(requestDTO)
                .exchange()
                .expectStatus().isNoContent();

        ProductEntity firstProduct = productRepository.findById(1).orElseThrow();
        ProductEntity secondProduct = productRepository.findById(2).orElseThrow();
        assertEquals(0, firstProduct.getStock());
        assertEquals(ProductStatus.OUT_OF_STOCK, firstProduct.getStatus());
        assertEquals(17, secondProduct.getStock());
        assertEquals(ProductStatus.ACTIVE, secondProduct.getStatus());
    }

    @Test
    void reserveStock_whenSecondProductHasInsufficientStock_thenRollbackEntireBatch()
    {
        StockReservationRequestDTO requestDTO = new StockReservationRequestDTO(List.of(
                new ItemReservationDTO(1, 50),
                new ItemReservationDTO(2, 21)
        ));

        restTestClient.patch()
                .uri("/reserve")
                .body(requestDTO)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.INSUFFICIENT_STOCK.name());

        ProductEntity firstProduct = productRepository.findById(1).orElseThrow();
        assertEquals(50, firstProduct.getStock());
        assertEquals(ProductStatus.ACTIVE, firstProduct.getStatus());
        assertEquals(20, productRepository.findById(2).orElseThrow().getStock());
    }

    @Test
    void reserveStock_whenSecondProductDoesNotExist_thenRollbackEntireBatch()
    {
        StockReservationRequestDTO requestDTO = new StockReservationRequestDTO(List.of(
                new ItemReservationDTO(1, 50),
                new ItemReservationDTO(999, 1)
        ));

        restTestClient.patch()
                .uri("/reserve")
                .body(requestDTO)
                .exchange()
                .expectStatus().isNotFound();

        ProductEntity product = productRepository.findById(1).orElseThrow();
        assertEquals(50, product.getStock());
        assertEquals(ProductStatus.ACTIVE, product.getStatus());
    }

    @Test
    void reserveStock_whenSecondProductIsInactive_thenRollbackEntireBatch()
    {
        ProductEntity secondProduct = productRepository.findById(2).orElseThrow();
        secondProduct.setStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(secondProduct);

        StockReservationRequestDTO requestDTO = new StockReservationRequestDTO(List.of(
                new ItemReservationDTO(1, 50),
                new ItemReservationDTO(2, 1)
        ));

        restTestClient.patch()
                .uri("/reserve")
                .body(requestDTO)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);

        ProductEntity firstProduct = productRepository.findById(1).orElseThrow();
        assertEquals(50, firstProduct.getStock());
        assertEquals(ProductStatus.ACTIVE, firstProduct.getStatus());
        secondProduct = productRepository.findById(2).orElseThrow();
        assertEquals(20, secondProduct.getStock());
        assertEquals(ProductStatus.INACTIVE, secondProduct.getStatus());
    }

    @Test
    void reserveStock_whenItemsAreEmpty_thenReturnBadRequest()
    {
        restTestClient.patch()
                .uri("/reserve")
                .body(new StockReservationRequestDTO(List.of()))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void reserveStock_whenQuantityIsInvalid_thenReturnBadRequestWithoutChangingStock()
    {
        StockReservationRequestDTO requestDTO = new StockReservationRequestDTO(List.of(
                new ItemReservationDTO(1, 2),
                new ItemReservationDTO(2, 0)
        ));

        restTestClient.patch()
                .uri("/reserve")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest();

        assertEquals(50, productRepository.findById(1).orElseThrow().getStock());
        assertEquals(20, productRepository.findById(2).orElseThrow().getStock());
    }

    @Test
    void reserveStock_whenItemIsNull_thenReturnBadRequest()
    {
        restTestClient.patch()
                .uri("/reserve")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"items\":[null]}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void reserveStock_whenApiKeyIsMissing_thenReturnUnauthorized()
    {
        RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/internal/products")
                .build()
                .patch()
                .uri("/reserve")
                .body(new StockReservationRequestDTO(List.of(new ItemReservationDTO(1, 2))))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void reserveStock_whenApiKeyIsInvalid_thenReturnUnauthorizedWithoutChangingStock()
    {
        int initialStock = productRepository.findById(1).orElseThrow().getStock();

        RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/internal/products")
                .build()
                .patch()
                .uri("/reserve")
                .header("X-API-Key", "invalid-key")
                .body(new StockReservationRequestDTO(List.of(new ItemReservationDTO(1, 2))))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.INVALID_API_KEY.name());

        assertEquals(initialStock, productRepository.findById(1).orElseThrow().getStock());
    }

    @Test
    void reserveStock_whenClientLacksPermission_thenReturnForbiddenWithoutChangingStock()
    {
        int initialStock = productRepository.findById(1).orElseThrow().getStock();

        RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/internal/products")
                .build()
                .patch()
                .uri("/reserve")
                .header("X-API-Key", "test-read-only-key")
                .body(new StockReservationRequestDTO(List.of(new ItemReservationDTO(1, 2))))
                .exchange()
                .expectStatus().isForbidden();

        assertEquals(initialStock, productRepository.findById(1).orElseThrow().getStock());
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
