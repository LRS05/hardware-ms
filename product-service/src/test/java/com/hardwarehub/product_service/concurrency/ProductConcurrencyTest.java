package com.hardwarehub.product_service.concurrency;

import com.hardwarehub.product_service.config.PostgresTestConfig;
import com.hardwarehub.product_service.dto.ProductUpdateRequestDTO;
import com.hardwarehub.product_service.dto.StockRequestDTO;
import com.hardwarehub.product_service.entity.ProductEntity;
import com.hardwarehub.product_service.enums.ProductStatus;
import com.hardwarehub.product_service.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@Sql(statements = "DELETE FROM products")
@Sql("/data.sql")
public class ProductConcurrencyTest
{
    @LocalServerPort
    private int port;

    @Value("${internal.clients.order-service.api-key}")
    private String apiKey;

    @Autowired
    private ProductRepository productRepository;

    @Value("${jwt.secret-key}")
    private String jwtSecret;

    @Value("${jwt.access.expiration}")
    private long accessTokenExpiration;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private TransactionStatus transaction;

    private RestTestClient adminRestTestClient;

    private RestTestClient restTestClient;

    private RestTestClient secondRestTestClient;

    private ExecutorService executor;

    @BeforeEach
    void setUp()
    {
        restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/internal/products")
                .defaultHeader("X-API-Key", apiKey)
                .build();

        secondRestTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/internal/products")
                .defaultHeader("X-API-Key", apiKey)
                .build();

        JwtEncoder jwtEncoder = NimbusJwtEncoder.withSecretKey(
                        new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512")
                )
                .algorithm(MacAlgorithm.HS512)
                .build();

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("1")
                .issuedAt(now)
                .expiresAt(now.plusMillis(accessTokenExpiration))
                .claim("type", "ACCESS")
                .claim("role", "ADMIN")
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS512).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        adminRestTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/api/v1/admin/products")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();

        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException
    {
        if (transaction != null && !transaction.isCompleted())
        {
            transactionManager.rollback(transaction);
        }

        executor.shutdownNow();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }

    @Test
    void decreaseStockById_whenConcurrentRequestsExceedStock_thenOnlyOneSucceeds() throws Exception
    {
        int productId = 1;

        ProductEntity product = productRepository.findById(productId).orElseThrow();

        product.setStock(5);
        productRepository.saveAndFlush(product);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> firstRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return restTestClient.patch()
                    .uri("/{id}/decrease", productId)
                    .body(new StockRequestDTO(4))
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        Future<Integer> secondRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return secondRestTestClient.patch()
                    .uri("/{id}/decrease", productId)
                    .body(new StockRequestDTO(4))
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();

        int firstStatus = firstRequest.get(15, TimeUnit.SECONDS);
        int secondStatus = secondRequest.get(15, TimeUnit.SECONDS);

        List<Integer> statuses = new ArrayList<>(List.of(firstStatus, secondStatus));
        Collections.sort(statuses);

        assertEquals(List.of(204, 409), statuses);

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(1, updatedProduct.getStock());
    }

    @Test
    void decreaseStockById_whenConcurrentRequestsHaveEnoughStock_thenBothSucceed() throws Exception
    {
        int productId = 1;

        ProductEntity product = productRepository.findById(productId).orElseThrow();

        product.setStock(5);
        productRepository.saveAndFlush(product);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> firstRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return restTestClient.patch()
                    .uri("/{id}/decrease", productId)
                    .body(new StockRequestDTO(2))
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        Future<Integer> secondRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return secondRestTestClient.patch()
                    .uri("/{id}/decrease", productId)
                    .body(new StockRequestDTO(2))
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();

        int firstStatus = firstRequest.get(15, TimeUnit.SECONDS);
        int secondStatus = secondRequest.get(15, TimeUnit.SECONDS);

        assertEquals(204, firstStatus);
        assertEquals(204, secondStatus);

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(1, updatedProduct.getStock());
        assertEquals(ProductStatus.ACTIVE, updatedProduct.getStatus());
    }

    @Test
    void decreaseStockById_whenConcurrentRequestsUseAllStock_thenMarkProductOutOfStock() throws Exception
    {
        int productId = 1;

        ProductEntity product = productRepository.findById(productId).orElseThrow();

        product.setStock(4);
        productRepository.saveAndFlush(product);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> firstRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return restTestClient.patch()
                    .uri("/{id}/decrease", productId)
                    .body(new StockRequestDTO(2))
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        Future<Integer> secondRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return secondRestTestClient.patch()
                    .uri("/{id}/decrease", productId)
                    .body(new StockRequestDTO(2))
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();

        int firstStatus = firstRequest.get(15, TimeUnit.SECONDS);
        int secondStatus = secondRequest.get(15, TimeUnit.SECONDS);

        assertEquals(204, firstStatus);
        assertEquals(204, secondStatus);

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(0, updatedProduct.getStock());
        assertEquals(ProductStatus.OUT_OF_STOCK, updatedProduct.getStatus());
    }

    @Test
    void increaseStockById_whenConcurrentRequests_thenKeepBothIncreases() throws Exception
    {
        int productId = 1;

        ProductEntity product = productRepository.findById(productId).orElseThrow();

        product.setStock(5);
        productRepository.saveAndFlush(product);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> firstRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return restTestClient.patch()
                    .uri("/{id}/increase", productId)
                    .body(new StockRequestDTO(3))
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        Future<Integer> secondRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return secondRestTestClient.patch()
                    .uri("/{id}/increase", productId)
                    .body(new StockRequestDTO(3))
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();

        int firstStatus = firstRequest.get(15, TimeUnit.SECONDS);
        int secondStatus = secondRequest.get(15, TimeUnit.SECONDS);

        assertEquals(204, firstStatus);
        assertEquals(204, secondStatus);

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(11, updatedProduct.getStock());
        assertEquals(ProductStatus.ACTIVE, updatedProduct.getStatus());
    }

    @Test
    void updateStock_whenConcurrentIncreaseAndDecrease_thenKeepBothChanges() throws Exception
    {
        int productId = 1;

        ProductEntity product = productRepository.findById(productId).orElseThrow();

        product.setStock(5);
        productRepository.saveAndFlush(product);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> firstRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return restTestClient.patch()
                    .uri("/{id}/increase", productId)
                    .body(new StockRequestDTO(3))
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        Future<Integer> secondRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return secondRestTestClient.patch()
                    .uri("/{id}/decrease", productId)
                    .body(new StockRequestDTO(4))
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();

        int firstStatus = firstRequest.get(15, TimeUnit.SECONDS);
        int secondStatus = secondRequest.get(15, TimeUnit.SECONDS);

        assertEquals(204, firstStatus);
        assertEquals(204, secondStatus);

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(4, updatedProduct.getStock());
        assertEquals(ProductStatus.ACTIVE, updatedProduct.getStatus());
    }

    @Test
    void updateById_whenConcurrentStockDecrease_thenKeepDescriptionAndStock() throws Exception
    {
        int productId = 1;

        ProductEntity product = productRepository.findById(productId).orElseThrow();

        product.setStock(5);
        productRepository.saveAndFlush(product);

        ProductUpdateRequestDTO requestDTO = new ProductUpdateRequestDTO(
                null,
                null,
                null,
                null,
                "Updated description",
                null,
                null
        );

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> firstRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return adminRestTestClient.patch()
                    .uri("/{id}", productId)
                    .body(requestDTO)
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        Future<Integer> secondRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return restTestClient.patch()
                    .uri("/{id}/decrease", productId)
                    .body(new StockRequestDTO(4))
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();

        int firstStatus = firstRequest.get(15, TimeUnit.SECONDS);
        int secondStatus = secondRequest.get(15, TimeUnit.SECONDS);

        assertEquals(200, firstStatus);
        assertEquals(204, secondStatus);

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals("Updated description", updatedProduct.getDescription());
        assertEquals(1, updatedProduct.getStock());
        assertEquals(ProductStatus.ACTIVE, updatedProduct.getStatus());
    }

    @Test
    void deactivateById_whenConcurrentStockDecrease_thenKeepConsistentStockAndStatus() throws Exception
    {
        int productId = 1;

        ProductEntity product = productRepository.findById(productId).orElseThrow();

        product.setStock(5);
        productRepository.saveAndFlush(product);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> firstRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return adminRestTestClient.patch()
                    .uri("/{id}/deactivate", productId)
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        Future<Integer> secondRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return restTestClient.patch()
                    .uri("/{id}/decrease", productId)
                    .body(new StockRequestDTO(4))
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();

        int firstStatus = firstRequest.get(15, TimeUnit.SECONDS);
        int secondStatus = secondRequest.get(15, TimeUnit.SECONDS);

        assertEquals(200, firstStatus);
        assertTrue(secondStatus == 204 || secondStatus == 409);

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(ProductStatus.INACTIVE, updatedProduct.getStatus());

        if (secondStatus == 204)
        {
            assertEquals(1, updatedProduct.getStock());
        }
        else
        {
            assertEquals(5, updatedProduct.getStock());
        }
    }

    @Test
    void deactivateById_whenConcurrentStockIncrease_thenKeepIncreasedStockAndInactiveStatus() throws Exception
    {
        int productId = 1;

        ProductEntity product = productRepository.findById(productId).orElseThrow();

        product.setStock(5);
        productRepository.saveAndFlush(product);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> firstRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return adminRestTestClient.patch()
                    .uri("/{id}/deactivate", productId)
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        Future<Integer> secondRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return restTestClient.patch()
                    .uri("/{id}/increase", productId)
                    .body(new StockRequestDTO(4))
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();

        int firstStatus = firstRequest.get(15, TimeUnit.SECONDS);
        int secondStatus = secondRequest.get(15, TimeUnit.SECONDS);

        assertEquals(200, firstStatus);
        assertEquals(204, secondStatus);

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(ProductStatus.INACTIVE, updatedProduct.getStatus());

        assertEquals(9, updatedProduct.getStock());
    }

    @Test
    void decreaseStockById_whenProductIsLocked_thenWaitAndValidateUpdatedStock() throws Exception
    {
        int productId = 1;

        ProductEntity product = productRepository.findById(productId).orElseThrow();

        product.setStock(5);
        productRepository.saveAndFlush(product);

        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
        ProductEntity lockedProduct = productRepository.findByIdForUpdate(productId).orElseThrow();
        int blockingPid = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);

        Future<Integer> request = executor.submit(() ->
        {
            return restTestClient.patch()
                    .uri("/{id}/decrease", productId)
                    .body(new StockRequestDTO(4))
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        waitUntilProductIsBlocked(blockingPid);
        assertFalse(request.isDone());

        lockedProduct.setStock(1);
        transactionManager.commit(transaction);

        assertEquals(409, request.get(15, TimeUnit.SECONDS));

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(1, updatedProduct.getStock());
        assertEquals(ProductStatus.ACTIVE, updatedProduct.getStatus());
    }

    @Test
    void increaseStockById_whenProductIsLocked_thenWaitAndIncreaseUpdatedStock() throws Exception
    {
        int productId = 1;

        ProductEntity product = productRepository.findById(productId).orElseThrow();

        product.setStock(5);
        productRepository.saveAndFlush(product);

        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
        ProductEntity lockedProduct = productRepository.findByIdForUpdate(productId).orElseThrow();
        int blockingPid = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);

        Future<Integer> request = executor.submit(() ->
        {
            return restTestClient.patch()
                    .uri("/{id}/increase", productId)
                    .body(new StockRequestDTO(3))
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        waitUntilProductIsBlocked(blockingPid);
        assertFalse(request.isDone());

        lockedProduct.setStock(8);
        transactionManager.commit(transaction);

        assertEquals(204, request.get(15, TimeUnit.SECONDS));

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(11, updatedProduct.getStock());
        assertEquals(ProductStatus.ACTIVE, updatedProduct.getStatus());
    }

    @Test
    void updateById_whenProductIsLocked_thenWaitAndKeepUpdatedStock() throws Exception
    {
        int productId = 1;

        ProductEntity product = productRepository.findById(productId).orElseThrow();

        product.setStock(5);
        productRepository.saveAndFlush(product);

        ProductUpdateRequestDTO requestDTO = new ProductUpdateRequestDTO(
                null,
                null,
                null,
                null,
                "Updated description",
                null,
                null
        );

        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
        ProductEntity lockedProduct = productRepository.findByIdForUpdate(productId).orElseThrow();
        int blockingPid = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);

        Future<Integer> request = executor.submit(() ->
        {
            return adminRestTestClient.patch()
                    .uri("/{id}", productId)
                    .body(requestDTO)
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        waitUntilProductIsBlocked(blockingPid);
        assertFalse(request.isDone());

        lockedProduct.setStock(1);
        transactionManager.commit(transaction);

        assertEquals(200, request.get(15, TimeUnit.SECONDS));

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(1, updatedProduct.getStock());
        assertEquals("Updated description", updatedProduct.getDescription());
        assertEquals(ProductStatus.ACTIVE, updatedProduct.getStatus());
    }

    @Test
    void deactivateById_whenProductIsLocked_thenWaitAndKeepUpdatedStock() throws Exception
    {
        int productId = 1;

        ProductEntity product = productRepository.findById(productId).orElseThrow();

        product.setStock(5);
        productRepository.saveAndFlush(product);

        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
        ProductEntity lockedProduct = productRepository.findByIdForUpdate(productId).orElseThrow();
        int blockingPid = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);

        Future<Integer> request = executor.submit(() ->
        {
            return adminRestTestClient.patch()
                    .uri("/{id}/deactivate", productId)
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        waitUntilProductIsBlocked(blockingPid);
        assertFalse(request.isDone());

        lockedProduct.setStock(1);
        transactionManager.commit(transaction);

        assertEquals(200, request.get(15, TimeUnit.SECONDS));

        ProductEntity updatedProduct = productRepository.findById(productId).orElseThrow();
        assertEquals(1, updatedProduct.getStock());
        assertEquals(ProductStatus.INACTIVE, updatedProduct.getStatus());
    }

    private void waitUntilProductIsBlocked(int blockingPid)
    {
        await().atMost(10, TimeUnit.SECONDS).until(() -> jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM pg_stat_activity
                    WHERE ? = ANY(pg_blocking_pids(pid))
                    AND query ILIKE '%FROM products%FOR UPDATE%'
                )
                """,
                Boolean.class,
                blockingPid
        ));
    }
}
