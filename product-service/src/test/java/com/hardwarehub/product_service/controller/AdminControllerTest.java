package com.hardwarehub.product_service.controller;

import com.hardwarehub.product_service.config.PostgresTestConfig;
import com.hardwarehub.product_service.dto.ProductRequestDTO;
import com.hardwarehub.product_service.dto.ProductUpdateRequestDTO;
import com.hardwarehub.product_service.entity.ProductEntity;
import com.hardwarehub.product_service.enums.ApiError;
import com.hardwarehub.product_service.enums.Category;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.*;

@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@Sql(statements = "DELETE FROM products")
@Sql("/data.sql")
@SpringBootTest(properties = "spring.config.location=classpath:/application-test.properties", webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AdminControllerTest
{
    @Autowired
    private ProductRepository productRepository;

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
                .baseUrl("http://localhost:" + port + "/api/v1/admin/products");

        if (role != null)
        {
            Instant now = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .subject("1")
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
    void getProductBySku_whenProductExists_thenReturnProduct()
    {
        adminRestTestClient.get()
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
        adminRestTestClient.get()
                .uri("?sku=missing")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.PRODUCT_NOT_FOUND.name())
                .jsonPath("$.message").isEqualTo("Product not found.");
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
                .baseUrl("http://localhost:" + port + "/api/v1/admin/products")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer INVALID_TOKEN")
                .build();

        invalidTokenClient.post()
                .uri("")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void getById_whenAdminRequestsInactiveProduct_thenReturnProduct()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.INACTIVE);
        product.setStock(50);
        productRepository.saveAndFlush(product);

        adminRestTestClient.get()
                .uri("/{id}", productId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(productId)
                .jsonPath("$.sku").isEqualTo(product.getSku())
                .jsonPath("$.stock").isEqualTo(50);
    }

    @Test
    void getById_whenAdminRequestsOutOfStockProduct_thenReturnProduct()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.OUT_OF_STOCK);
        product.setStock(0);
        productRepository.saveAndFlush(product);

        adminRestTestClient.get()
                .uri("/{id}", productId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(productId)
                .jsonPath("$.sku").isEqualTo(product.getSku())
                .jsonPath("$.stock").isEqualTo(0);
    }

    @Test
    void getById_whenStaffRequestsInactiveProduct_thenReturnProduct()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.INACTIVE);
        product.setStock(50);
        productRepository.saveAndFlush(product);

        staffRestTestClient.get()
                .uri("/{id}", productId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(productId)
                .jsonPath("$.sku").isEqualTo(product.getSku())
                .jsonPath("$.stock").isEqualTo(50);
    }

    @Test
    void getById_whenStaffRequestsOutOfStockProduct_thenReturnProduct()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.OUT_OF_STOCK);
        product.setStock(0);
        productRepository.saveAndFlush(product);

        staffRestTestClient.get()
                .uri("/{id}", productId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(productId)
                .jsonPath("$.sku").isEqualTo(product.getSku())
                .jsonPath("$.stock").isEqualTo(0);
    }

    @Test
    void getById_whenRequestIsUnauthenticated_thenReturnUnauthorized()
    {
        restTestClient.get()
                .uri("/{id}", 1)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void getById_whenCustomerIsAuthenticated_thenReturnForbidden()
    {
        customerRestTestClient.get()
                .uri("/{id}", 1)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getProductBySku_whenAdminRequestsInactiveProduct_thenReturnProduct()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.INACTIVE);
        product.setStock(50);
        productRepository.saveAndFlush(product);

        adminRestTestClient.get()
                .uri("?sku={sku}", product.getSku())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(productId)
                .jsonPath("$.sku").isEqualTo(product.getSku())
                .jsonPath("$.stock").isEqualTo(50);
    }

    @Test
    void getProductBySku_whenAdminRequestsOutOfStockProduct_thenReturnProduct()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.OUT_OF_STOCK);
        product.setStock(0);
        productRepository.saveAndFlush(product);

        adminRestTestClient.get()
                .uri("?sku={sku}", product.getSku())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(productId)
                .jsonPath("$.sku").isEqualTo(product.getSku())
                .jsonPath("$.stock").isEqualTo(0);
    }

    @Test
    void getProductBySku_whenStaffRequestsInactiveProduct_thenReturnProduct()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.INACTIVE);
        product.setStock(50);
        productRepository.saveAndFlush(product);

        staffRestTestClient.get()
                .uri("?sku={sku}", product.getSku())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(productId)
                .jsonPath("$.sku").isEqualTo(product.getSku())
                .jsonPath("$.stock").isEqualTo(50);
    }

    @Test
    void getProductBySku_whenStaffRequestsOutOfStockProduct_thenReturnProduct()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.OUT_OF_STOCK);
        product.setStock(0);
        productRepository.saveAndFlush(product);

        staffRestTestClient.get()
                .uri("?sku={sku}", product.getSku())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(productId)
                .jsonPath("$.sku").isEqualTo(product.getSku())
                .jsonPath("$.stock").isEqualTo(0);
    }

    @Test
    void getProductBySku_whenRequestIsUnauthenticated_thenReturnUnauthorized()
    {
        restTestClient.get()
                .uri("?sku={sku}", "77490")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void getProductBySku_whenCustomerIsAuthenticated_thenReturnForbidden()
    {
        customerRestTestClient.get()
                .uri("?sku={sku}", "77490")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getById_whenProductDoesNotExist_thenReturnNotFound()
    {
        adminRestTestClient.get()
                .uri("/{id}", 999)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.PRODUCT_NOT_FOUND.name())
                .jsonPath("$.message").isEqualTo("Product not found.");
    }

    @Test
    void createProduct_whenActiveProductHasNoStock_thenMarkProductOutOfStock()
    {
        ProductRequestDTO requestDTO = new ProductRequestDTO(
                ProductStatus.ACTIVE,
                Category.MOTHERBOARD,
                "ZERO-STOCK-001",
                "Zero stock motherboard",
                "A motherboard without stock",
                BigDecimal.valueOf(300),
                0
        );

        adminRestTestClient.post()
                .uri("")
                .body(requestDTO)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.sku").isEqualTo(requestDTO.sku())
                .jsonPath("$.stock").isEqualTo(0);

        ProductEntity savedProduct = productRepository.findBySku(requestDTO.sku()).orElseThrow();
        assertEquals(0, savedProduct.getStock());
        assertEquals(ProductStatus.OUT_OF_STOCK, savedProduct.getStatus());
    }

    @Test
    void createProduct_whenInactiveProductHasNoStock_thenKeepInactive()
    {
        ProductRequestDTO requestDTO = new ProductRequestDTO(
                ProductStatus.INACTIVE,
                Category.MOTHERBOARD,
                "ZERO-STOCK-001",
                "Zero stock motherboard",
                "A motherboard without stock",
                BigDecimal.valueOf(300),
                0
        );

        adminRestTestClient.post()
                .uri("")
                .body(requestDTO)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.sku").isEqualTo(requestDTO.sku())
                .jsonPath("$.stock").isEqualTo(0);

        ProductEntity savedProduct = productRepository.findBySku(requestDTO.sku()).orElseThrow();
        assertEquals(0, savedProduct.getStock());
        assertEquals(ProductStatus.INACTIVE, savedProduct.getStatus());
    }

    @Test
    void createProduct_whenStockIsNegative_thenReturnBadRequest()
    {
        ProductRequestDTO requestDTO = new ProductRequestDTO(
                ProductStatus.ACTIVE,
                Category.MOTHERBOARD,
                "NEGATIVE-STOCK-001",
                "Negative stock motherboard",
                "A motherboard with invalid stock",
                BigDecimal.valueOf(300),
                -1
        );

        adminRestTestClient.post()
                .uri("")
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.stock").isEqualTo("Stock must be greater than or equal to zero.");

        assertFalse(productRepository.existsBySku(requestDTO.sku()));
    }

    @Test
    void updateProductById_whenActiveProductStockReachesZero_thenMarkProductOutOfStock()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.ACTIVE);
        product.setStock(50);
        productRepository.saveAndFlush(product);

        ProductUpdateRequestDTO requestDTO = new ProductUpdateRequestDTO(
                null,
                null,
                null,
                null,
                null,
                null,
                0
        );

        staffRestTestClient.patch()
                .uri("/{id}", productId)
                .body(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(productId)
                .jsonPath("$.stock").isEqualTo(0);

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(0, updatedProduct.getStock());
        assertEquals(ProductStatus.OUT_OF_STOCK, updatedProduct.getStatus());
    }

    @Test
    void updateProductById_whenOutOfStockProductIsRestocked_thenActivateProduct()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.OUT_OF_STOCK);
        product.setStock(0);
        productRepository.saveAndFlush(product);

        ProductUpdateRequestDTO requestDTO = new ProductUpdateRequestDTO(
                null,
                null,
                null,
                null,
                null,
                null,
                10
        );

        staffRestTestClient.patch()
                .uri("/{id}", productId)
                .body(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(productId)
                .jsonPath("$.stock").isEqualTo(10);

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(10, updatedProduct.getStock());
        assertEquals(ProductStatus.ACTIVE, updatedProduct.getStatus());
    }

    @Test
    void updateProductById_whenInactiveProductIsRestocked_thenKeepInactive()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.INACTIVE);
        product.setStock(0);
        productRepository.saveAndFlush(product);

        ProductUpdateRequestDTO requestDTO = new ProductUpdateRequestDTO(
                null,
                null,
                null,
                null,
                null,
                null,
                10
        );

        staffRestTestClient.patch()
                .uri("/{id}", productId)
                .body(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(productId)
                .jsonPath("$.stock").isEqualTo(10);

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(10, updatedProduct.getStock());
        assertEquals(ProductStatus.INACTIVE, updatedProduct.getStatus());
    }

    @Test
    void updateProductById_whenInactiveProductStockReachesZero_thenKeepInactive()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.INACTIVE);
        product.setStock(50);
        productRepository.saveAndFlush(product);

        ProductUpdateRequestDTO requestDTO = new ProductUpdateRequestDTO(
                null,
                null,
                null,
                null,
                null,
                null,
                0
        );

        staffRestTestClient.patch()
                .uri("/{id}", productId)
                .body(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(productId)
                .jsonPath("$.stock").isEqualTo(0);

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(0, updatedProduct.getStock());
        assertEquals(ProductStatus.INACTIVE, updatedProduct.getStatus());
    }

    @Test
    void updateProductById_whenInactiveProductIsActivatedWithStock_thenActivateProduct()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.INACTIVE);
        product.setStock(50);
        productRepository.saveAndFlush(product);

        ProductUpdateRequestDTO requestDTO = new ProductUpdateRequestDTO(
                ProductStatus.ACTIVE,
                null,
                null,
                null,
                null,
                null,
                null
        );

        staffRestTestClient.patch()
                .uri("/{id}", productId)
                .body(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(productId)
                .jsonPath("$.stock").isEqualTo(50);

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(50, updatedProduct.getStock());
        assertEquals(ProductStatus.ACTIVE, updatedProduct.getStatus());
    }

    @Test
    void updateProductById_whenInactiveProductIsActivatedWithoutStock_thenMarkProductOutOfStock()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.INACTIVE);
        product.setStock(0);
        productRepository.saveAndFlush(product);

        ProductUpdateRequestDTO requestDTO = new ProductUpdateRequestDTO(
                ProductStatus.ACTIVE,
                null,
                null,
                null,
                null,
                null,
                null
        );

        staffRestTestClient.patch()
                .uri("/{id}", productId)
                .body(requestDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(productId)
                .jsonPath("$.stock").isEqualTo(0);

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(0, updatedProduct.getStock());
        assertEquals(ProductStatus.OUT_OF_STOCK, updatedProduct.getStatus());
    }

    @Test
    void updateProductById_whenStockIsNegative_thenReturnBadRequest()
    {
        int productId = 1;
        ProductUpdateRequestDTO requestDTO = new ProductUpdateRequestDTO(
                null,
                null,
                null,
                null,
                null,
                null,
                -1
        );

        staffRestTestClient.patch()
                .uri("/{id}", productId)
                .body(requestDTO)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.stock").isEqualTo("Stock must be greater than or equal to zero.");

        ProductEntity product = productRepository.findById(productId).orElseThrow();
        assertEquals(50, product.getStock());
        assertEquals(ProductStatus.ACTIVE, product.getStatus());
    }
}
