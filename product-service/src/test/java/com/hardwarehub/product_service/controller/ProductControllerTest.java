package com.hardwarehub.product_service.controller;

import com.hardwarehub.product_service.config.PostgresTestConfig;
import com.hardwarehub.product_service.entity.ProductEntity;
import com.hardwarehub.product_service.enums.ApiError;
import com.hardwarehub.product_service.enums.Category;
import com.hardwarehub.product_service.enums.ProductStatus;
import com.hardwarehub.product_service.factory.ProductFactory;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.spec.SecretKeySpec;

@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@Sql(statements = "DELETE FROM products")
@Sql("/data.sql")
@SpringBootTest(properties = "spring.config.location=classpath:/application-test.properties", webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ProductControllerTest
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
    void getLatestUpdatedProducts_whenRequestIsUnauthenticated_thenReturnProductPage()
    {
        RestTestClient unauthenticatedClient = createRestTestClientForRole(null);

        unauthenticatedClient.get()
                .uri("")
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
    void getProductById_whenRequestIsUnauthenticated_thenReturnProduct()
    {
        RestTestClient unauthenticatedClient = createRestTestClientForRole(null);

        unauthenticatedClient.get()
                .uri("/{id}", 1)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
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
    void getProductById_whenUnauthenticatedRequestRequestsInactiveProduct_thenReturnConflict()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.INACTIVE);
        product.setStock(50);
        productRepository.saveAndFlush(product);

        restTestClient.get()
                .uri("/{id}", productId)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.PRODUCT_OUT_OF_STOCK.name());
    }

    @Test
    void getProductById_whenUnauthenticatedRequestRequestsOutOfStockProduct_thenReturnConflict()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.OUT_OF_STOCK);
        product.setStock(0);
        productRepository.saveAndFlush(product);

        restTestClient.get()
                .uri("/{id}", productId)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.PRODUCT_OUT_OF_STOCK.name());
    }

    @Test
    void getLatestUpdatedProducts_whenUnauthenticatedRequestSearchesProductsWithDifferentStatuses_thenReturnOnlyActiveProducts()
    {
        ProductEntity inactiveProduct = ProductFactory.graphicCard();
        inactiveProduct.setId(0);
        inactiveProduct.setSku("INACTIVE-GPU");
        inactiveProduct.setName("NVIDIA inactive graphics card");
        inactiveProduct.setStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(inactiveProduct);

        ProductEntity outOfStockProduct = ProductFactory.graphicCard();
        outOfStockProduct.setId(0);
        outOfStockProduct.setSku("OUT-OF-STOCK-GPU");
        outOfStockProduct.setName("NVIDIA out of stock graphics card");
        outOfStockProduct.setStatus(ProductStatus.OUT_OF_STOCK);
        outOfStockProduct.setStock(0);
        productRepository.saveAndFlush(outOfStockProduct);

        restTestClient.get()
                .uri("")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.content[?(@.id == 1)]").isNotEmpty()
                .jsonPath("$.content[?(@.sku == 'INACTIVE-GPU')]").isEmpty()
                .jsonPath("$.content[?(@.sku == 'OUT-OF-STOCK-GPU')]").isEmpty();
    }

    @Test
    void getProductsByName_whenUnauthenticatedRequestSearchesProductsWithDifferentStatuses_thenReturnOnlyActiveProducts()
    {
        ProductEntity inactiveProduct = ProductFactory.graphicCard();
        inactiveProduct.setId(0);
        inactiveProduct.setSku("INACTIVE-GPU");
        inactiveProduct.setName("NVIDIA inactive graphics card");
        inactiveProduct.setStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(inactiveProduct);

        ProductEntity outOfStockProduct = ProductFactory.graphicCard();
        outOfStockProduct.setId(0);
        outOfStockProduct.setSku("OUT-OF-STOCK-GPU");
        outOfStockProduct.setName("NVIDIA out of stock graphics card");
        outOfStockProduct.setStatus(ProductStatus.OUT_OF_STOCK);
        outOfStockProduct.setStock(0);
        productRepository.saveAndFlush(outOfStockProduct);

        restTestClient.get()
                .uri("?name=NVIDIA")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[?(@.id == 1)]").isNotEmpty()
                .jsonPath("$.content[?(@.sku == 'INACTIVE-GPU')]").isEmpty()
                .jsonPath("$.content[?(@.sku == 'OUT-OF-STOCK-GPU')]").isEmpty();
    }

    @Test
    void getProductsByCategory_whenUnauthenticatedRequestSearchesProductsWithDifferentStatuses_thenReturnOnlyActiveProducts()
    {
        ProductEntity inactiveProduct = ProductFactory.graphicCard();
        inactiveProduct.setId(0);
        inactiveProduct.setSku("INACTIVE-GPU");
        inactiveProduct.setName("NVIDIA inactive graphics card");
        inactiveProduct.setStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(inactiveProduct);

        ProductEntity outOfStockProduct = ProductFactory.graphicCard();
        outOfStockProduct.setId(0);
        outOfStockProduct.setSku("OUT-OF-STOCK-GPU");
        outOfStockProduct.setName("NVIDIA out of stock graphics card");
        outOfStockProduct.setStatus(ProductStatus.OUT_OF_STOCK);
        outOfStockProduct.setStock(0);
        productRepository.saveAndFlush(outOfStockProduct);

        restTestClient.get()
                .uri("?category=GRAPHIC_CARD")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[?(@.id == 1)]").isNotEmpty()
                .jsonPath("$.content[?(@.sku == 'INACTIVE-GPU')]").isEmpty()
                .jsonPath("$.content[?(@.sku == 'OUT-OF-STOCK-GPU')]").isEmpty();
    }

    @Test
    void getProductById_whenCustomerRequestsInactiveProduct_thenReturnConflict()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.INACTIVE);
        product.setStock(50);
        productRepository.saveAndFlush(product);

        customerRestTestClient.get()
                .uri("/{id}", productId)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.PRODUCT_OUT_OF_STOCK.name());
    }

    @Test
    void getProductById_whenCustomerRequestsOutOfStockProduct_thenReturnConflict()
    {
        int productId = 1;
        ProductEntity product = productRepository.findById(productId).orElseThrow();
        product.setStatus(ProductStatus.OUT_OF_STOCK);
        product.setStock(0);
        productRepository.saveAndFlush(product);

        customerRestTestClient.get()
                .uri("/{id}", productId)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT)
                .expectBody()
                .jsonPath("$.error").isEqualTo(ApiError.PRODUCT_OUT_OF_STOCK.name());
    }

    @Test
    void getLatestUpdatedProducts_whenCustomerSearchesProductsWithDifferentStatuses_thenReturnOnlyActiveProducts()
    {
        ProductEntity inactiveProduct = ProductFactory.graphicCard();
        inactiveProduct.setId(0);
        inactiveProduct.setSku("INACTIVE-GPU");
        inactiveProduct.setName("NVIDIA inactive graphics card");
        inactiveProduct.setStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(inactiveProduct);

        ProductEntity outOfStockProduct = ProductFactory.graphicCard();
        outOfStockProduct.setId(0);
        outOfStockProduct.setSku("OUT-OF-STOCK-GPU");
        outOfStockProduct.setName("NVIDIA out of stock graphics card");
        outOfStockProduct.setStatus(ProductStatus.OUT_OF_STOCK);
        outOfStockProduct.setStock(0);
        productRepository.saveAndFlush(outOfStockProduct);

        customerRestTestClient.get()
                .uri("")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.content[?(@.id == 1)]").isNotEmpty()
                .jsonPath("$.content[?(@.sku == 'INACTIVE-GPU')]").isEmpty()
                .jsonPath("$.content[?(@.sku == 'OUT-OF-STOCK-GPU')]").isEmpty();
    }

    @Test
    void getProductsByName_whenCustomerSearchesProductsWithDifferentStatuses_thenReturnOnlyActiveProducts()
    {
        ProductEntity inactiveProduct = ProductFactory.graphicCard();
        inactiveProduct.setId(0);
        inactiveProduct.setSku("INACTIVE-GPU");
        inactiveProduct.setName("NVIDIA inactive graphics card");
        inactiveProduct.setStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(inactiveProduct);

        ProductEntity outOfStockProduct = ProductFactory.graphicCard();
        outOfStockProduct.setId(0);
        outOfStockProduct.setSku("OUT-OF-STOCK-GPU");
        outOfStockProduct.setName("NVIDIA out of stock graphics card");
        outOfStockProduct.setStatus(ProductStatus.OUT_OF_STOCK);
        outOfStockProduct.setStock(0);
        productRepository.saveAndFlush(outOfStockProduct);

        customerRestTestClient.get()
                .uri("?name=NVIDIA")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[?(@.id == 1)]").isNotEmpty()
                .jsonPath("$.content[?(@.sku == 'INACTIVE-GPU')]").isEmpty()
                .jsonPath("$.content[?(@.sku == 'OUT-OF-STOCK-GPU')]").isEmpty();
    }

    @Test
    void getProductsByCategory_whenCustomerSearchesProductsWithDifferentStatuses_thenReturnOnlyActiveProducts()
    {
        ProductEntity inactiveProduct = ProductFactory.graphicCard();
        inactiveProduct.setId(0);
        inactiveProduct.setSku("INACTIVE-GPU");
        inactiveProduct.setName("NVIDIA inactive graphics card");
        inactiveProduct.setStatus(ProductStatus.INACTIVE);
        productRepository.saveAndFlush(inactiveProduct);

        ProductEntity outOfStockProduct = ProductFactory.graphicCard();
        outOfStockProduct.setId(0);
        outOfStockProduct.setSku("OUT-OF-STOCK-GPU");
        outOfStockProduct.setName("NVIDIA out of stock graphics card");
        outOfStockProduct.setStatus(ProductStatus.OUT_OF_STOCK);
        outOfStockProduct.setStock(0);
        productRepository.saveAndFlush(outOfStockProduct);

        customerRestTestClient.get()
                .uri("?category=GRAPHIC_CARD")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.content[?(@.id == 1)]").isNotEmpty()
                .jsonPath("$.content[?(@.sku == 'INACTIVE-GPU')]").isEmpty()
                .jsonPath("$.content[?(@.sku == 'OUT-OF-STOCK-GPU')]").isEmpty();
    }

    @Test
    void getProductById_whenCustomerRequestsActiveProduct_thenReturnProduct()
    {
        customerRestTestClient.get()
                .uri("/{id}", 1)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.stock").isEqualTo(50);
    }
}
