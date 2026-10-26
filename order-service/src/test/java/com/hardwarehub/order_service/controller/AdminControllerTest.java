package com.hardwarehub.order_service.controller;

import com.hardwarehub.order_service.config.PostgresTestConfig;
import com.hardwarehub.order_service.enums.ApiError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@Sql(statements = {"DELETE FROM order_items", "DELETE FROM orders"})
@Sql("/data.sql")
public class AdminControllerTest
{
    @LocalServerPort
    private int port;

    private RestTestClient unauthenticatedRestTestClient;

    private RestTestClient customerRestTestClient;

    private RestTestClient staffRestTestClient;

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

        unauthenticatedRestTestClient = createRestTestClientForRole(null, 0);
        customerRestTestClient = createRestTestClientForRole("CUSTOMER", 3);
        staffRestTestClient = createRestTestClientForRole("STAFF", 2);
        adminRestTestClient = createRestTestClientForRole("ADMIN", 1);
    }

    private RestTestClient createRestTestClientForRole(String role, int userId)
    {
        RestTestClient.Builder builder = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/api/v1/admin/users");

        if (role != null)
        {
            Instant now = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .subject("admin-controller-test")
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
    void getAllByUserId_whenAdminRequestsOrders_thenReturnOrdersPage()
    {
        int userId = 1;

        adminRestTestClient.get()
                .uri("/{userId}/orders", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.number").isEqualTo(0)
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.content[0].total_price").isEqualTo(2000)
                .jsonPath("$.content[0].order_items[0].product_name").isEqualTo("NVIDIA GTX 1080-TI");
    }

    @Test
    void getAllByUserId_whenAdminRequestsPage_thenReturnRequestedPage()
    {
        int userId = 1;
        int page = 1;
        int size = 1;

        adminRestTestClient.get()
                .uri("/{userId}/orders?page={page}&size={size}", userId, page, size)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.totalPages").isEqualTo(2)
                .jsonPath("$.number").isEqualTo(page)
                .jsonPath("$.size").isEqualTo(size)
                .jsonPath("$.content.length()").isEqualTo(1);
    }

    @Test
    void getAllByUserId_whenAdminRequestsUserWithoutOrders_thenReturnEmptyPage()
    {
        int userId = 999;

        adminRestTestClient.get()
                .uri("/{userId}/orders", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(0)
                .jsonPath("$.content.length()").isEqualTo(0);
    }

    @Test
    void getById_whenAdminRequestsExistingOrder_thenReturnOrder()
    {
        int orderId = 1;

        adminRestTestClient.get()
                .uri("/orders/{id}", orderId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(orderId)
                .jsonPath("$.total_price").isEqualTo(2000)
                .jsonPath("$.order_items.length()").isEqualTo(1)
                .jsonPath("$.order_items[0].product_name").isEqualTo("NVIDIA GTX 1080-TI")
                .jsonPath("$.order_items[0].quantity").isEqualTo(2);
    }

    @Test
    void getById_whenAdminRequestsMissingOrder_thenReturnNotFound()
    {
        int orderId = 999;

        adminRestTestClient.get()
                .uri("/orders/{id}", orderId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.ORDER_NOT_FOUND.name())
                .jsonPath("$.message").isEqualTo("Order not found.");
    }

    @Test
    void getAllByUserId_whenStaffRequestsOrders_thenReturnOrdersPage()
    {
        int userId = 1;

        staffRestTestClient.get()
                .uri("/{userId}/orders", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.number").isEqualTo(0)
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.content[0].total_price").isEqualTo(2000)
                .jsonPath("$.content[0].order_items[0].product_name").isEqualTo("NVIDIA GTX 1080-TI");
    }

    @Test
    void getAllByUserId_whenStaffRequestsPage_thenReturnRequestedPage()
    {
        int userId = 1;
        int page = 1;
        int size = 1;

        staffRestTestClient.get()
                .uri("/{userId}/orders?page={page}&size={size}", userId, page, size)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.totalPages").isEqualTo(2)
                .jsonPath("$.number").isEqualTo(page)
                .jsonPath("$.size").isEqualTo(size)
                .jsonPath("$.content.length()").isEqualTo(1);
    }

    @Test
    void getAllByUserId_whenStaffRequestsUserWithoutOrders_thenReturnEmptyPage()
    {
        int userId = 999;

        staffRestTestClient.get()
                .uri("/{userId}/orders", userId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(0)
                .jsonPath("$.content.length()").isEqualTo(0);
    }

    @Test
    void getById_whenStaffRequestsExistingOrder_thenReturnOrder()
    {
        int orderId = 1;

        staffRestTestClient.get()
                .uri("/orders/{id}", orderId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(orderId)
                .jsonPath("$.total_price").isEqualTo(2000)
                .jsonPath("$.order_items.length()").isEqualTo(1)
                .jsonPath("$.order_items[0].product_name").isEqualTo("NVIDIA GTX 1080-TI")
                .jsonPath("$.order_items[0].quantity").isEqualTo(2);
    }

    @Test
    void getById_whenStaffRequestsMissingOrder_thenReturnNotFound()
    {
        int orderId = 999;

        staffRestTestClient.get()
                .uri("/orders/{id}", orderId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.ORDER_NOT_FOUND.name())
                .jsonPath("$.message").isEqualTo("Order not found.");
    }

    @Test
    void accessAdminEndpoints_whenCustomerRole_thenReturnForbidden()
    {
        customerRestTestClient.get()
                .uri("")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void accessAdminEndpoints_whenUnauthenticated_thenReturnUnauthorized()
    {
        unauthenticatedRestTestClient.get()
                .uri("")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void accessAdminEndpoints_whenInvalidToken_thenReturnUnauthorized()
    {
        RestTestClient invalidTokenClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/api/v1/admin/users")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer INVALID_TOKEN")
                .build();

        invalidTokenClient.get()
                .uri("")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
