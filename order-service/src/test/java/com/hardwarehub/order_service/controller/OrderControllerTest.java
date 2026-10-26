package com.hardwarehub.order_service.controller;

import com.hardwarehub.order_service.client.ProductClient;
import com.hardwarehub.order_service.client.dto.ProductResponseDTO;
import com.hardwarehub.order_service.config.PostgresTestConfig;
import com.hardwarehub.order_service.enums.ApiError;
import com.hardwarehub.order_service.enums.OrderStatus;
import com.hardwarehub.order_service.exception.ProductNotFoundException;
import com.hardwarehub.order_service.exception.ProductOutOfStockException;
import com.hardwarehub.order_service.factory.ProductFactory;
import com.hardwarehub.order_service.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@Sql(statements = {"DELETE FROM order_items", "DELETE FROM orders"})
@Sql("/data.sql")
public class OrderControllerTest
{
    @LocalServerPort
    private int port;

    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private ProductClient productClient;

    private RestTestClient restTestClient;

    private RestTestClient customerRestTestClient;

    private RestTestClient adminRestTestClient;

    @Value("${jwt.secret-key}")
    private String jwtSecret;

    @Value("${jwt.access.expiration}")
    private long accessTokenExpiration;

    private JwtEncoder jwtEncoder;

    @BeforeEach
    void setUp()
    {
        jwtEncoder = NimbusJwtEncoder.withSecretKey(
                        new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512")
                )
                .algorithm(MacAlgorithm.HS512)
                .build();

        restTestClient = createRestTestClientForRole(null, 0);
        customerRestTestClient = createRestTestClientForRole("CUSTOMER", 1);
        adminRestTestClient = createRestTestClientForRole("ADMIN", 2);

        ProductResponseDTO graphicCard = ProductFactory.graphicCardDTO();
        ProductResponseDTO processor = ProductFactory.processorDTO();

        when(productClient.getById(graphicCard.id())).thenReturn(graphicCard);
        when(productClient.getById(processor.id())).thenReturn(processor);
    }

    private RestTestClient createRestTestClientForRole(String role, int userId)
    {
        RestTestClient.Builder builder = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/api/v1/orders");

        if (role != null)
        {
            Instant now = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .subject("order-controller-test")
                    .issuedAt(now)
                    .expiresAt(now.plusMillis(accessTokenExpiration))
                    .claim("userId", userId)
                    .claim("type", "ACCESS")
                    .claim("role", role)
                    .build();

            JwsHeader header = JwsHeader.with(MacAlgorithm.HS512).build();
            String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }

        return builder.build();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void createByUserId_whenNoActiveOrderExists_thenReturnNoContent()
    {
        int userId = 2;

        customerRestTestClient.post()
                .uri("/users/{userId}", userId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void createByUserId_whenActiveOrderAlreadyExists_thenReturnNoContent()
    {
        int userId = 1;

        customerRestTestClient.post()
                .uri("/users/{userId}", userId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void getActive_whenCustomerHasActiveOrder_thenReturnOrder()
    {
        customerRestTestClient.get()
                .uri("/active")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(2)
                .jsonPath("$.total_price").isEqualTo(2000)
                .jsonPath("$.order_items[0].quantity").isEqualTo(2);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void getPending_whenCustomerHasPendingOrder_thenReturnOrder()
    {
        customerRestTestClient.patch()
                .uri("/active/checkout")
                .exchange()
                .expectStatus().isOk();

        customerRestTestClient.get()
                .uri("/pending")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(2)
                .jsonPath("$.total_price").isEqualTo(2000)
                .jsonPath("$.order_items[0].quantity").isEqualTo(2);
    }

    @Test
    void getById_whenOrderBelongsToCustomer_thenReturnOrder()
    {
        int orderId = 1;

        customerRestTestClient.get()
                .uri("/{id}", orderId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.total_price").isEqualTo(2000)
                .jsonPath("$.order_items[0].product_name").isEqualTo("NVIDIA GTX 1080-TI");
    }

    @Test
    void getAll_whenHistoryExists_thenExcludeActiveOrder()
    {
        customerRestTestClient.get()
                .uri("")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].id").isEqualTo(1);
    }

    @Test
    void getAll_whenPageIsRequested_thenReturnRequestedPage()
    {
        int page = 1;
        int size = 1;

        customerRestTestClient.get()
                .uri("?page={page}&size={size}", page, size)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.number").isEqualTo(1)
                .jsonPath("$.content.length()").isEqualTo(0);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void getAll_whenCustomerHasNoHistory_thenReturnEmptyPage()
    {
        orderRepository.deleteById(1);

        customerRestTestClient.get()
                .uri("")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(0)
                .jsonPath("$.content.length()").isEqualTo(0);
    }

    @Test
    void getAllByStatus_whenStatusIsCompleted_thenReturnMatchingOrders()
    {
        OrderStatus status = OrderStatus.COMPLETED;

        customerRestTestClient.get()
                .uri("?status={status}", status)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].id").isEqualTo(1);
    }

    @Test
    void getAllByStatus_whenStatusIsActive_thenReturnMatchingOrders()
    {
        OrderStatus status = OrderStatus.ACTIVE;

        customerRestTestClient.get()
                .uri("?status={status}", status)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[0].id").isEqualTo(2);
    }

    @Test
    void getAllByStatus_whenStatusIsPending_thenReturnMatchingOrders()
    {
        OrderStatus status = OrderStatus.PENDING;

        customerRestTestClient.get()
                .uri("?status={status}", status)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(0)
                .jsonPath("$.content.length()").isEqualTo(0);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void addItemToOrder_whenProductIsNew_thenReturnNoContent()
    {
        int productId = 2;

        customerRestTestClient.patch()
                .uri("/active/items/products/{productId}", productId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void addItemToOrder_whenProductAlreadyExists_thenReturnNoContent()
    {
        int productId = 1;

        customerRestTestClient.patch()
                .uri("/active/items/products/{productId}", productId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void updateItemQuantity_whenQuantityIsAvailable_thenReturnNoContent()
    {
        int itemId = 2;
        int quantity = 3;

        customerRestTestClient.patch()
                .uri("/active/items/{itemId}?quantity={quantity}", itemId, quantity)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void updateItemQuantity_whenQuantityIsOne_thenReturnNoContent()
    {
        int itemId = 2;
        int quantity = 1;

        customerRestTestClient.patch()
                .uri("/active/items/{itemId}?quantity={quantity}", itemId, quantity)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void updateItemQuantity_whenQuantityIsEqualToStock_thenReturnNoContent()
    {
        int itemId = 2;
        int quantity = 10;

        customerRestTestClient.patch()
                .uri("/active/items/{itemId}?quantity={quantity}", itemId, quantity)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void updateItemQuantity_whenQuantityIsGreaterThanStock_thenRejectQuantity()
    {
        int itemId = 2;
        int quantity = 11;

        customerRestTestClient.patch()
                .uri("/active/items/{itemId}?quantity={quantity}", itemId, quantity)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.PRODUCT_OUT_OF_STOCK.name())
                .jsonPath("$.message").isEqualTo("Only 10 units are available.");
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void updateItemQuantity_whenQuantityIsZero_thenRejectQuantity()
    {
        int itemId = 2;
        int quantity = 0;

        customerRestTestClient.patch()
                .uri("/active/items/{itemId}?quantity={quantity}", itemId, quantity)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.INVALID_QUANTITY.name())
                .jsonPath("$.message").isEqualTo("Quantity must be greater than zero.");
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void updateItemQuantity_whenQuantityIsNegative_thenRejectQuantity()
    {
        int itemId = 2;
        int quantity = -1;

        customerRestTestClient.patch()
                .uri("/active/items/{itemId}?quantity={quantity}", itemId, quantity)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.INVALID_QUANTITY.name())
                .jsonPath("$.message").isEqualTo("Quantity must be greater than zero.");
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void deleteItemById_whenOtherItemsRemain_thenReturnNoContent()
    {
        int productId = 2;
        int itemId = 2;

        customerRestTestClient.patch()
                .uri("/active/items/products/{productId}", productId)
                .exchange()
                .expectStatus().isNoContent();

        customerRestTestClient.delete()
                .uri("/active/items/{itemId}", itemId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void deleteItemById_whenItemIsLast_thenReturnNoContent()
    {
        int itemId = 2;

        customerRestTestClient.delete()
                .uri("/active/items/{itemId}", itemId)
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void refresh_whenOrderIsEmpty_thenReturnNoContent()
    {
        customerRestTestClient.patch()
                .uri("/active/clear")
                .exchange()
                .expectStatus().isNoContent();

        customerRestTestClient.patch()
                .uri("/active/refresh")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void clear_whenOrderHasMultipleItems_thenReturnNoContent()
    {
        int productId = 2;

        customerRestTestClient.patch()
                .uri("/active/items/products/{productId}", productId)
                .exchange()
                .expectStatus().isNoContent();

        customerRestTestClient.patch()
                .uri("/active/clear")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void clear_whenOrderIsAlreadyEmpty_thenReturnNoContent()
    {
        customerRestTestClient.patch()
                .uri("/active/clear")
                .exchange()
                .expectStatus().isNoContent();

        customerRestTestClient.patch()
                .uri("/active/clear")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void checkout_whenOrderIsEmpty_thenReturnBadRequest()
    {
        customerRestTestClient.patch()
                .uri("/active/clear")
                .exchange()
                .expectStatus().isNoContent();

        customerRestTestClient.patch()
                .uri("/active/checkout")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.EMPTY_ORDER.name())
                .jsonPath("$.message").isEqualTo("Cannot checkout an empty order.");
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void reopen_whenOrderIsPending_thenReturnReopenedOrder()
    {
        customerRestTestClient.patch()
                .uri("/active/checkout")
                .exchange()
                .expectStatus().isOk();

        customerRestTestClient.patch()
                .uri("/pending/reopen")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(2)
                .jsonPath("$.total_price").isEqualTo(2000)
                .jsonPath("$.order_items[0].quantity").isEqualTo(2);
    }

    @Nested
    class LimitedStock
    {
        @BeforeEach
        void setUpProductClient()
        {
            ProductResponseDTO product = ProductFactory.graphicCardDTO(2);

            when(productClient.getById(product.id())).thenReturn(product);
        }

        @Test
        @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
        void addItemToOrder_whenIncrementExceedsStock_thenReturnConflict()
        {
            int productId = 1;

            customerRestTestClient.patch()
                    .uri("/active/items/products/{productId}", productId)
                    .exchange()
                    .expectStatus().isEqualTo(409)
                    .expectBody()
                    .jsonPath("$.error").isEqualTo(ApiError.PRODUCT_OUT_OF_STOCK.name())
                    .jsonPath("$.message").isEqualTo("Only 2 units are available.");
        }
    }

    @Nested
    class ChangedPrice
    {
        @BeforeEach
        void setUpProductClient()
        {
            ProductResponseDTO product = ProductFactory.graphicCardDTO(10, 1500);

            when(productClient.getById(product.id())).thenReturn(product);
        }

        @Test
        @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
        void refresh_whenProductPriceChanges_thenReturnNoContent()
        {
            customerRestTestClient.patch()
                    .uri("/active/refresh")
                    .exchange()
                    .expectStatus().isNoContent();
        }

        @Test
        @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
        void checkout_whenOrderHasItems_thenReturnOrderWithUpdatedPrices()
        {
            customerRestTestClient.patch()
                    .uri("/active/checkout")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.id").isEqualTo(2)
                    .jsonPath("$.total_price").isEqualTo(3000)
                    .jsonPath("$.order_items[0].quantity").isEqualTo(2);
        }
    }

    @Nested
    class ReducedStock
    {
        @BeforeEach
        void setUpProductClient()
        {
            ProductResponseDTO product = ProductFactory.graphicCardDTO(1);

            when(productClient.getById(product.id())).thenReturn(product);
        }

        @Test
        @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
        void refresh_whenQuantityExceedsStock_thenReturnNoContent()
        {
            customerRestTestClient.patch()
                    .uri("/active/refresh")
                    .exchange()
                    .expectStatus().isNoContent();
        }
    }

    @Nested
    class MissingProduct
    {
        @BeforeEach
        void setUpProductClient()
        {
            when(productClient.getById(1)).thenThrow(new ProductNotFoundException("Product not found."));
        }

        @Test
        @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
        void refresh_whenProductDoesNotExist_thenReturnNoContent()
        {
            int productId = 2;

            customerRestTestClient.patch()
                    .uri("/active/items/products/{productId}", productId)
                    .exchange()
                    .expectStatus().isNoContent();

            customerRestTestClient.patch()
                    .uri("/active/refresh")
                    .exchange()
                    .expectStatus().isNoContent();
        }

        @Test
        @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
        void checkout_whenAllProductsAreRemoved_thenReturnBadRequest()
        {
            customerRestTestClient.patch()
                    .uri("/active/checkout")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody()
                    .jsonPath("$.error").isEqualTo(ApiError.EMPTY_ORDER.name())
                    .jsonPath("$.message").isEqualTo("Cannot checkout an empty order.");
        }
    }

    @Nested
    class OutOfStockProduct
    {
        @BeforeEach
        void setUpProductClient()
        {
            when(productClient.getById(1)).thenThrow(new ProductOutOfStockException("Product unavailable."));
        }

        @Test
        @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
        void refresh_whenProductHasNoStock_thenReturnNoContent()
        {
            int productId = 2;

            customerRestTestClient.patch()
                    .uri("/active/items/products/{productId}", productId)
                    .exchange()
                    .expectStatus().isNoContent();

            customerRestTestClient.patch()
                    .uri("/active/refresh")
                    .exchange()
                    .expectStatus().isNoContent();
        }
    }

    @Nested
    class StockReservationFailure
    {
        @BeforeEach
        void setUpProductClient()
        {
            doThrow(new ProductOutOfStockException("Insufficient stock.")).when(productClient).decreaseStockById(1, 2);
        }

        @Test
        @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
        void checkout_whenStockReservationFails_thenReturnConflict()
        {
            customerRestTestClient.patch()
                    .uri("/active/checkout")
                    .exchange()
                    .expectStatus().isEqualTo(409)
                    .expectBody()
                    .jsonPath("$.error").isEqualTo(ApiError.PRODUCT_OUT_OF_STOCK.name())
                    .jsonPath("$.message").isEqualTo("Insufficient stock.");
        }
    }
    @Test
    void accessOrderEndpoints_whenUnauthenticated_thenReturnUnauthorized()
    {
        restTestClient.get()
                .uri("")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void accessOrderEndpoints_whenInvalidToken_thenReturnUnauthorized()
    {
        RestTestClient invalidTokenClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/api/v1/orders")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer INVALID_TOKEN")
                .build();

        invalidTokenClient.get()
                .uri("")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void accessOrderEndpoints_whenAdminRole_thenReturnForbidden()
    {
        adminRestTestClient.get()
                .uri("")
                .exchange()
                .expectStatus().isForbidden();
    }
}
