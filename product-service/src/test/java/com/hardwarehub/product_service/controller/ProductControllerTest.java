package com.hardwarehub.product_service.controller;

import com.hardwarehub.product_service.config.PostgresTestConfig;
import com.hardwarehub.product_service.dto.ProductRequestDTO;
import com.hardwarehub.product_service.dto.ProductUpdateRequestDTO;
import com.hardwarehub.product_service.dto.StockRequestDTO;
import com.hardwarehub.product_service.enums.ApiError;
import com.hardwarehub.product_service.enums.Category;
import com.hardwarehub.product_service.enums.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@Sql(statements = "DELETE FROM products")
@Sql("/data.sql")
public class ProductControllerTest
{
    @LocalServerPort
    private int port;

    @Value("${jwt.secret-key}")
    private String jwtSecret;

    @Value("${jwt.access.expiration}")
    private long accessTokenExpiration;

    private RestTestClient restTestClient;

    private RestTestClient adminRestTestClient;

    private RestTestClient staffRestTestClient;

    private RestTestClient customerRestTestClient;

    private JwtEncoder jwtEncoder;

    @BeforeEach
    void setUp()
    {
        jwtEncoder = NimbusJwtEncoder.withSecretKey(
                        new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512")
                )
                .algorithm(MacAlgorithm.HS512)
                .build();

        restTestClient = createRestTestClientForRole(null);
        adminRestTestClient = createRestTestClientForRole("ADMIN");
        staffRestTestClient = createRestTestClientForRole("STAFF");
        customerRestTestClient = createRestTestClientForRole("CUSTOMER");
    }

    private RestTestClient createRestTestClientForRole(String role)
    {
        RestTestClient.Builder builder = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/api/v1/products");

        if (role != null)
        {
            Instant now = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .subject("product-controller-test")
                    .issuedAt(now)
                    .expiresAt(now.plusMillis(accessTokenExpiration))
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
    void getLatestUpdatedProducts_whenProductsExist_thenReturnProductPage()
    {
        restTestClient.get()
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.size").isEqualTo(20);
    }

    @Test
    void getProductsByName_whenNameMatches_thenReturnMatchingProducts()
    {
        restTestClient.get()
                .uri("?name=Intel")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].name").isEqualTo("Intel Core i3-4170")
                .jsonPath("$.content[0].sku").isEqualTo("77490");
    }

    @Test
    void getProductsByName_whenNoProductsMatch_thenReturnEmptyPage()
    {
        restTestClient.get()
                .uri("?name=missing")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(0)
                .jsonPath("$.content.length()").isEqualTo(0);
    }

    @Test
    void getProductBySku_whenProductExists_thenReturnProduct()
    {
        restTestClient.get()
                .uri("?sku=77490")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sku").isEqualTo("77490")
                .jsonPath("$.name").isEqualTo("Intel Core i3-4170")
                .jsonPath("$.stock").isEqualTo(20);
    }

    @Test
    void getProductBySku_whenProductDoesNotExist_thenReturnNotFound()
    {
        restTestClient.get()
                .uri("?sku=missing")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.PRODUCT_NOT_FOUND.name())
                .jsonPath("$.message").isEqualTo("Product not found.");
    }

    @Test
    void getProductsByCategory_whenProductsWithCategoryExist_thenReturnProductPage()
    {
        restTestClient.get()
                .uri("?category=GRAPHIC_CARD")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].category").isEqualTo(Category.GRAPHIC_CARD.name())
                .jsonPath("$.content[0].sku").isEqualTo("GV-N108T GAMING-OC-11G");
    }

    @Test
    void getProductsByCategory_whenCategoryIsInvalid_thenReturnBadRequest()
    {
        restTestClient.get()
                .uri("?category=INVALID")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.METHOD_ARGUMENT_TYPE_MISMATCH.name())
                .jsonPath("$.message").isEqualTo("Invalid parameter type.");
    }

    @Test
    void getProductById_whenProductExists_thenReturnProduct()
    {
        int productId = 1;

        restTestClient.get()
                .uri("/{id}", productId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(productId)
                .jsonPath("$.sku").isEqualTo("GV-N108T GAMING-OC-11G")
                .jsonPath("$.name").isEqualTo("NVIDIA GTX 1080 TI");
    }

    @Test
    void getProductById_whenProductDoesNotExist_thenReturnNotFound()
    {
        int productId = 999;

        restTestClient.get()
                .uri("/{id}", productId)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.PRODUCT_NOT_FOUND.name())
                .jsonPath("$.message").isEqualTo("Product not found.");
    }

    @Test
    void getProductById_whenIdIsInvalid_thenReturnBadRequest()
    {
        restTestClient.get()
                .uri("/invalid")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.METHOD_ARGUMENT_TYPE_MISMATCH.name())
                .jsonPath("$.message").isEqualTo("Invalid parameter type.");
    }

    @Test
    void createProduct_whenAdminProvidesValidData_thenReturnCreatedProduct()
    {
        ProductRequestDTO requestDTO = new ProductRequestDTO(
                ProductStatus.ACTIVE,
                Category.MOTHERBOARD,
                "TEST-MOTHERBOARD-001",
                "Test motherboard",
                "Test motherboard description",
                BigDecimal.valueOf(300),
                15
        );

        adminRestTestClient.post()
                .uri("")
                .body(requestDTO)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.sku").isEqualTo(requestDTO.sku())
                .jsonPath("$.name").isEqualTo(requestDTO.name())
                .jsonPath("$.stock").isEqualTo(requestDTO.stock());
    }

    @Test
    void createProduct_whenStaffProvidesValidData_thenReturnCreatedProduct()
    {
        ProductRequestDTO requestDTO = new ProductRequestDTO(
                ProductStatus.ACTIVE,
                Category.MOTHERBOARD,
                "TEST-MOTHERBOARD-002",
                "Staff motherboard",
                "Staff motherboard description",
                BigDecimal.valueOf(400),
                10
        );

        staffRestTestClient.post()
                .uri("")
                .body(requestDTO)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.sku").isEqualTo(requestDTO.sku())
                .jsonPath("$.name").isEqualTo(requestDTO.name())
                .jsonPath("$.stock").isEqualTo(requestDTO.stock());
    }

    @Test
    void createProduct_whenNameAlreadyExists_thenReturnConflict()
    {
        ProductRequestDTO requestDTO = new ProductRequestDTO(
                ProductStatus.ACTIVE,
                Category.MOTHERBOARD,
                "NEW-MOTHERBOARD-001",
                "NVIDIA GTX 1080 TI",
                "A duplicate product name",
                BigDecimal.valueOf(300),
                15
        );

        adminRestTestClient.post()
                .uri("")
                .body(requestDTO)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.PRODUCT_NAME_ALREADY_EXISTS.name())
                .jsonPath("$.message").isEqualTo("Name already exists.");
    }

    @Test
    void createProduct_whenSkuAlreadyExists_thenReturnConflict()
    {
        ProductRequestDTO requestDTO = new ProductRequestDTO(
                ProductStatus.ACTIVE,
                Category.MOTHERBOARD,
                "77490",
                "A new product name",
                "A duplicate product SKU",
                BigDecimal.valueOf(300),
                15
        );

        adminRestTestClient.post()
                .uri("")
                .body(requestDTO)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.PRODUCT_SKU_ALREADY_EXISTS.name())
                .jsonPath("$.message").isEqualTo("SKU already exists.");
    }

    @Test
    void createProduct_whenRequiredFieldsAreMissing_thenReturnBadRequest()
    {
        ProductRequestDTO requestDTO = new ProductRequestDTO(
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        adminRestTestClient.post()
                .uri("")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo("Status is required.")
                .jsonPath("$.category").isEqualTo("Category is required.")
                .jsonPath("$.sku").isEqualTo("SKU is required.");
    }

    @Test
    void updateProductById_whenRequestIsValid_thenReturnUpdatedProduct()
    {
        int productId = 1;
        ProductUpdateRequestDTO requestDTO = new ProductUpdateRequestDTO(
                null,
                Category.MOTHERBOARD,
                "UPDATED-GPU-001",
                "Updated graphics card",
                null,
                BigDecimal.valueOf(1500),
                35
        );

        staffRestTestClient.patch()
                .uri("/{id}", productId)
                .body(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sku").isEqualTo(requestDTO.sku())
                .jsonPath("$.name").isEqualTo(requestDTO.name())
                .jsonPath("$.stock").isEqualTo(requestDTO.stock());
    }

    @Test
    void updateProductById_whenProductDoesNotExist_thenReturnNotFound()
    {
        int productId = 999;
        ProductUpdateRequestDTO requestDTO = new ProductUpdateRequestDTO(
                null,
                null,
                null,
                "Updated product",
                null,
                null,
                null
        );

        adminRestTestClient.patch()
                .uri("/{id}", productId)
                .body(requestDTO)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.PRODUCT_NOT_FOUND.name())
                .jsonPath("$.message").isEqualTo("Product not found.");
    }

    @Test
    void deactivateProductById_whenProductExists_thenReturnSuccessMessage()
    {
        int productId = 1;

        staffRestTestClient.patch()
                .uri("/{id}/deactivate", productId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo("Product with id " + productId + " successfully deactivated.");
    }

    @Test
    void decreaseStockById_whenEnoughStockExists_thenReturnNoContent()
    {
        int productId = 1;
        StockRequestDTO requestDTO = new StockRequestDTO(10);

        adminRestTestClient.patch()
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

        adminRestTestClient.patch()
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

        staffRestTestClient.patch()
                .uri("/{id}/increase", productId)
                .body(requestDTO)
                .exchange()
                .expectStatus().isNoContent();
    }

    // Authorization tests

    @Test
    void createProduct_whenRequestIsUnauthenticated_thenReturnUnauthorized()
    {
        ProductRequestDTO requestDTO = new ProductRequestDTO(
                ProductStatus.ACTIVE,
                Category.MOTHERBOARD,
                "UNAUTHENTICATED-MOTHERBOARD-001",
                "Unauthenticated motherboard",
                "Unauthenticated motherboard description",
                BigDecimal.valueOf(300),
                15
        );

        restTestClient.post()
                .uri("")
                .body(requestDTO)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void createProduct_whenCustomerIsAuthenticated_thenReturnForbidden()
    {
        ProductRequestDTO requestDTO = new ProductRequestDTO(
                ProductStatus.ACTIVE,
                Category.MOTHERBOARD,
                "CUSTOMER-MOTHERBOARD-001",
                "Customer motherboard",
                "Customer motherboard description",
                BigDecimal.valueOf(300),
                15
        );

        customerRestTestClient.post()
                .uri("")
                .body(requestDTO)
                .exchange()
                .expectStatus().isForbidden();
    }
    @Test
    void createProduct_whenInvalidToken_thenReturnUnauthorized()
    {
        RestTestClient invalidTokenClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/api/v1/products")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer INVALID_TOKEN")
                .build();

        invalidTokenClient.post()
                .uri("")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
