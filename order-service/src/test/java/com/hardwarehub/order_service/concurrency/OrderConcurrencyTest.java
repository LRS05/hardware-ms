package com.hardwarehub.order_service.concurrency;

import com.hardwarehub.order_service.client.ProductClient;
import com.hardwarehub.order_service.client.dto.ProductResponseDTO;
import com.hardwarehub.order_service.client.dto.ItemReservationDTO;
import com.hardwarehub.order_service.client.dto.StockReservationRequestDTO;
import com.hardwarehub.order_service.config.PostgresTestConfig;
import com.hardwarehub.order_service.entity.OrderEntity;
import com.hardwarehub.order_service.entity.OrderItemEntity;
import com.hardwarehub.order_service.enums.OrderStatus;
import com.hardwarehub.order_service.exception.InvalidOrderStatusException;
import com.hardwarehub.order_service.factory.ProductFactory;
import com.hardwarehub.order_service.repository.OrderItemRepository;
import com.hardwarehub.order_service.repository.OrderRepository;
import com.hardwarehub.order_service.service.OrderService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
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
import static org.mockito.Mockito.*;

@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@Sql(statements = {"DELETE FROM order_items", "DELETE FROM orders"})
@Sql("/data.sql")
@SpringBootTest(properties = "spring.config.location=classpath:/application-test.properties", webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OrderConcurrencyTest
{
    @LocalServerPort
    private int port;

    @Value("${jwt.secret-key}")
    private String jwtSecret;

    @Value("${jwt.access.expiration}")
    private long accessTokenExpiration;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ProductClient productClient;

    private TransactionStatus transaction;

    private RestTestClient restTestClient;

    private RestTestClient secondRestTestClient;

    private ExecutorService executor;

    @BeforeEach
    void setUp()
    {
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
                .claim("role", "CUSTOMER")
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS512).build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/api/v1/orders")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();

        secondRestTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/api/v1/orders")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();

        ProductResponseDTO graphicCard = ProductFactory.graphicCardDTO();
        ProductResponseDTO processor = ProductFactory.processorDTO();

        when(productClient.getById(graphicCard.id())).thenReturn(graphicCard);
        when(productClient.getById(processor.id())).thenReturn(processor);

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
    void addItem_whenConcurrentRequestsHaveNoActiveOrder_thenKeepOneOpenOrder() throws Exception
    {
        int userId = 1;
        int productId = 1;

        jdbcTemplate.update("DELETE FROM order_items WHERE order_id = 2");
        jdbcTemplate.update("DELETE FROM orders WHERE id = 2");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> firstRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return restTestClient.patch()
                    .uri("/active/items/products/{productId}", productId)
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
                    .uri("/active/items/products/{productId}", productId)
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

        assertTrue(statuses.equals(List.of(204, 204)) || statuses.equals(List.of(204, 409)));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE user_id = ? AND status IN ('ACTIVE', 'PENDING')",
                Integer.class,
                userId
        ));

        OrderEntity updatedOrder = orderRepository.findByUserIdAndStatus(userId, OrderStatus.ACTIVE).orElseThrow();
        OrderItemEntity updatedItem = orderItemRepository.findByOrderIdAndProductId(updatedOrder.getId(), productId).orElseThrow();

        int expectedQuantity = 2;
        if (statuses.contains(409))
        {
            expectedQuantity = 1;
        }

        assertEquals(expectedQuantity, updatedItem.getQuantity());
        assertEquals(BigDecimal.valueOf(expectedQuantity * 1000L).setScale(2), updatedItem.getPrice());
        assertEquals(updatedItem.getPrice(), updatedOrder.getTotalPrice());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_items WHERE order_id = ?",
                Integer.class,
                updatedOrder.getId()
        ));
        verify(productClient, never()).reserveStock(any(StockReservationRequestDTO.class));
        verify(productClient, never()).releaseStock(any(StockReservationRequestDTO.class));
    }

    @Test
    void addItem_whenConcurrentRequestsAddSameNewProduct_thenKeepOneItem() throws Exception
    {
        int orderId = 2;
        int productId = 2;

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> firstRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return restTestClient.patch()
                    .uri("/active/items/products/{productId}", productId)
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
                    .uri("/active/items/products/{productId}", productId)
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

        OrderEntity updatedOrder = orderRepository.findById(orderId).orElseThrow();
        OrderItemEntity updatedItem = orderItemRepository.findByOrderIdAndProductId(orderId, productId).orElseThrow();

        assertEquals(2, updatedItem.getQuantity());
        assertEquals(new BigDecimal("400.00"), updatedItem.getPrice());
        assertEquals(new BigDecimal("2400.00"), updatedOrder.getTotalPrice());
        assertEquals(OrderStatus.ACTIVE, updatedOrder.getStatus());
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_items WHERE order_id = ?",
                Integer.class,
                orderId
        ));
        verify(productClient, never()).reserveStock(any(StockReservationRequestDTO.class));
        verify(productClient, never()).releaseStock(any(StockReservationRequestDTO.class));
    }

    @Test
    void addItem_whenConcurrentRequestsIncrementExistingItem_thenKeepBothIncrements() throws Exception
    {
        int orderId = 2;
        int productId = 1;

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> firstRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return restTestClient.patch()
                    .uri("/active/items/products/{productId}", productId)
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
                    .uri("/active/items/products/{productId}", productId)
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

        OrderEntity updatedOrder = orderRepository.findById(orderId).orElseThrow();
        OrderItemEntity updatedItem = orderItemRepository.findByOrderIdAndProductId(orderId, productId).orElseThrow();

        assertEquals(4, updatedItem.getQuantity());
        assertEquals(new BigDecimal("4000.00"), updatedItem.getPrice());
        assertEquals(new BigDecimal("4000.00"), updatedOrder.getTotalPrice());
        assertEquals(OrderStatus.ACTIVE, updatedOrder.getStatus());
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_items WHERE order_id = ?",
                Integer.class,
                orderId
        ));
        verify(productClient, never()).reserveStock(any(StockReservationRequestDTO.class));
        verify(productClient, never()).releaseStock(any(StockReservationRequestDTO.class));
    }

    @Test
    void updateItemQuantity_whenConcurrentRequestsChangeDifferentItems_thenKeepQuantitiesAndTotal() throws Exception
    {
        int orderId = 2;
        int itemId = 2;

        restTestClient.patch()
                .uri("/active/items/products/{productId}", 2)
                .exchange()
                .expectStatus().isNoContent();

        OrderItemEntity secondItem = orderItemRepository.findByOrderIdAndProductId(orderId, 2).orElseThrow();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> firstRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return restTestClient.patch()
                    .uri("/active/items/{itemId}?quantity={quantity}", itemId, 3)
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
                    .uri("/active/items/{itemId}?quantity={quantity}", secondItem.getId(), 4)
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

        OrderEntity updatedOrder = orderRepository.findById(orderId).orElseThrow();
        OrderItemEntity updatedFirstItem = orderItemRepository.findById(itemId).orElseThrow();
        OrderItemEntity updatedSecondItem = orderItemRepository.findById(secondItem.getId()).orElseThrow();

        assertEquals(3, updatedFirstItem.getQuantity());
        assertEquals(new BigDecimal("3000.00"), updatedFirstItem.getPrice());
        assertEquals(4, updatedSecondItem.getQuantity());
        assertEquals(new BigDecimal("800.00"), updatedSecondItem.getPrice());
        assertEquals(new BigDecimal("3800.00"), updatedOrder.getTotalPrice());
        assertEquals(OrderStatus.ACTIVE, updatedOrder.getStatus());
        verify(productClient, never()).reserveStock(any(StockReservationRequestDTO.class));
        verify(productClient, never()).releaseStock(any(StockReservationRequestDTO.class));
    }

    @Test
    void checkout_whenConcurrentRequestsUseSameOrder_thenReserveStockOnce() throws Exception
    {
        int orderId = 2;

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> firstRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return restTestClient.patch()
                    .uri("/active/checkout")
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
                    .uri("/active/checkout")
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

        assertEquals(List.of(200, 400), statuses);

        OrderEntity updatedOrder = orderRepository.findById(orderId).orElseThrow();
        OrderItemEntity updatedItem = orderItemRepository.findById(2).orElseThrow();

        assertEquals(OrderStatus.PENDING, updatedOrder.getStatus());
        assertEquals(new BigDecimal("2000.00"), updatedOrder.getTotalPrice());
        assertEquals(2, updatedItem.getQuantity());
        verify(productClient, times(1)).reserveStock(new StockReservationRequestDTO(List.of(new ItemReservationDTO(1, 2))));
        verify(productClient, never()).releaseStock(any(StockReservationRequestDTO.class));
    }

    @Test
    void reopen_whenConcurrentRequestsUseSamePendingOrder_thenReleaseStockOnce() throws Exception
    {
        int orderId = 2;

        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.PENDING);
        orderRepository.saveAndFlush(order);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Integer> firstRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return restTestClient.patch()
                    .uri("/pending/reopen")
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
                    .uri("/pending/reopen")
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

        assertEquals(List.of(200, 404), statuses);

        OrderEntity updatedOrder = orderRepository.findById(orderId).orElseThrow();
        OrderItemEntity updatedItem = orderItemRepository.findById(2).orElseThrow();

        assertEquals(OrderStatus.ACTIVE, updatedOrder.getStatus());
        assertEquals(new BigDecimal("2000.00"), updatedOrder.getTotalPrice());
        assertEquals(2, updatedItem.getQuantity());
        verify(productClient, times(1)).releaseStock(new StockReservationRequestDTO(List.of(new ItemReservationDTO(1, 2))));
        verify(productClient, never()).reserveStock(any(StockReservationRequestDTO.class));
    }

    @Test
    void complete_whenConcurrentOperationsUseSamePendingOrder_thenBothSucceed() throws Exception
    {
        int orderId = 2;

        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.PENDING);
        orderRepository.saveAndFlush(order);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Void> firstRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            orderService.complete(orderId);
            return null;
        });

        Future<Void> secondRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            orderService.complete(orderId);
            return null;
        });

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();

        firstRequest.get(15, TimeUnit.SECONDS);
        secondRequest.get(15, TimeUnit.SECONDS);

        OrderEntity updatedOrder = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.COMPLETED, updatedOrder.getStatus());
        assertEquals(new BigDecimal("2000.00"), updatedOrder.getTotalPrice());
        verifyNoInteractions(productClient);
    }

    @Test
    void complete_whenConcurrentReopenUsesSamePendingOrder_thenKeepOneTransition() throws Exception
    {
        int orderId = 2;

        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.PENDING);
        orderRepository.saveAndFlush(order);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Boolean> firstRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            try
            {
                orderService.complete(orderId);
                return true;
            }
            catch (InvalidOrderStatusException e)
            {
                return false;
            }
        });

        Future<Integer> secondRequest = executor.submit(() ->
        {
            ready.countDown();
            assertTrue(start.await(10, TimeUnit.SECONDS));

            return restTestClient.patch()
                    .uri("/pending/reopen")
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();

        boolean completed = firstRequest.get(15, TimeUnit.SECONDS);
        int reopenStatus = secondRequest.get(15, TimeUnit.SECONDS);

        OrderEntity updatedOrder = orderRepository.findById(orderId).orElseThrow();
        assertEquals(new BigDecimal("2000.00"), updatedOrder.getTotalPrice());

        if (completed)
        {
            assertEquals(404, reopenStatus);
            assertEquals(OrderStatus.COMPLETED, updatedOrder.getStatus());
            verify(productClient, never()).releaseStock(any(StockReservationRequestDTO.class));
        }
        else
        {
            assertEquals(200, reopenStatus);
            assertEquals(OrderStatus.ACTIVE, updatedOrder.getStatus());
            verify(productClient, times(1)).releaseStock(new StockReservationRequestDTO(List.of(new ItemReservationDTO(1, 2))));
        }

        verify(productClient, never()).reserveStock(any(StockReservationRequestDTO.class));
    }

    @Test
    void addItem_whenOrderIsLocked_thenWaitAndIncrementUpdatedQuantity() throws Exception
    {
        int userId = 1;
        int orderId = 2;

        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
        OrderEntity lockedOrder = orderRepository.findByUserIdAndStatusForUpdate(userId, OrderStatus.ACTIVE.name()).orElseThrow();
        int blockingPid = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);

        Future<Integer> request = executor.submit(() ->
        {
            return restTestClient.patch()
                    .uri("/active/items/products/{productId}", 1)
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        waitUntilOrderIsBlocked(blockingPid);
        assertFalse(request.isDone());

        OrderItemEntity lockedItem = orderItemRepository.findById(2).orElseThrow();
        lockedItem.setQuantity(3);
        lockedItem.setPrice(new BigDecimal("3000.00"));
        lockedOrder.setTotalPrice(new BigDecimal("3000.00"));
        transactionManager.commit(transaction);

        assertEquals(204, request.get(15, TimeUnit.SECONDS));

        OrderEntity updatedOrder = orderRepository.findById(orderId).orElseThrow();
        OrderItemEntity updatedItem = orderItemRepository.findById(2).orElseThrow();
        assertEquals(4, updatedItem.getQuantity());
        assertEquals(new BigDecimal("4000.00"), updatedItem.getPrice());
        assertEquals(new BigDecimal("4000.00"), updatedOrder.getTotalPrice());
        assertEquals(OrderStatus.ACTIVE, updatedOrder.getStatus());
        verify(productClient, never()).reserveStock(any(StockReservationRequestDTO.class));
        verify(productClient, never()).releaseStock(any(StockReservationRequestDTO.class));
    }

    @Test
    void updateItemQuantity_whenOrderIsLocked_thenWaitAndKeepOtherItemChanges() throws Exception
    {
        int userId = 1;
        int orderId = 2;

        restTestClient.patch()
                .uri("/active/items/products/{productId}", 2)
                .exchange()
                .expectStatus().isNoContent();

        OrderItemEntity secondItem = orderItemRepository.findByOrderIdAndProductId(orderId, 2).orElseThrow();

        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
        OrderEntity lockedOrder = orderRepository.findByUserIdAndStatusForUpdate(userId, OrderStatus.ACTIVE.name()).orElseThrow();
        int blockingPid = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);

        Future<Integer> request = executor.submit(() ->
        {
            return restTestClient.patch()
                    .uri("/active/items/{itemId}?quantity={quantity}", 2, 3)
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        waitUntilOrderIsBlocked(blockingPid);
        assertFalse(request.isDone());

        OrderItemEntity lockedItem = orderItemRepository.findById(secondItem.getId()).orElseThrow();
        lockedItem.setQuantity(3);
        lockedItem.setPrice(new BigDecimal("600.00"));
        lockedOrder.setTotalPrice(new BigDecimal("2600.00"));
        transactionManager.commit(transaction);

        assertEquals(204, request.get(15, TimeUnit.SECONDS));

        OrderEntity updatedOrder = orderRepository.findById(orderId).orElseThrow();
        OrderItemEntity updatedFirstItem = orderItemRepository.findById(2).orElseThrow();
        OrderItemEntity updatedSecondItem = orderItemRepository.findById(secondItem.getId()).orElseThrow();
        assertEquals(3, updatedFirstItem.getQuantity());
        assertEquals(new BigDecimal("3000.00"), updatedFirstItem.getPrice());
        assertEquals(3, updatedSecondItem.getQuantity());
        assertEquals(new BigDecimal("600.00"), updatedSecondItem.getPrice());
        assertEquals(new BigDecimal("3600.00"), updatedOrder.getTotalPrice());
        assertEquals(OrderStatus.ACTIVE, updatedOrder.getStatus());
        verify(productClient, never()).reserveStock(any(StockReservationRequestDTO.class));
        verify(productClient, never()).releaseStock(any(StockReservationRequestDTO.class));
    }

    @Test
    void deleteItemById_whenOrderIsLocked_thenWaitAndKeepRemainingItemChanges() throws Exception
    {
        int userId = 1;
        int orderId = 2;

        restTestClient.patch()
                .uri("/active/items/products/{productId}", 2)
                .exchange()
                .expectStatus().isNoContent();

        OrderItemEntity secondItem = orderItemRepository.findByOrderIdAndProductId(orderId, 2).orElseThrow();

        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
        OrderEntity lockedOrder = orderRepository.findByUserIdAndStatusForUpdate(userId, OrderStatus.ACTIVE.name()).orElseThrow();
        int blockingPid = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);

        Future<Integer> request = executor.submit(() ->
        {
            return restTestClient.delete()
                    .uri("/active/items/{itemId}", 2)
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        waitUntilOrderIsBlocked(blockingPid);
        assertFalse(request.isDone());

        OrderItemEntity lockedItem = orderItemRepository.findById(secondItem.getId()).orElseThrow();
        lockedItem.setQuantity(3);
        lockedItem.setPrice(new BigDecimal("600.00"));
        lockedOrder.setTotalPrice(new BigDecimal("2600.00"));
        transactionManager.commit(transaction);

        assertEquals(204, request.get(15, TimeUnit.SECONDS));

        OrderEntity updatedOrder = orderRepository.findById(orderId).orElseThrow();
        OrderItemEntity remainingItem = orderItemRepository.findById(secondItem.getId()).orElseThrow();
        assertFalse(orderItemRepository.existsById(2));
        assertEquals(3, remainingItem.getQuantity());
        assertEquals(new BigDecimal("600.00"), remainingItem.getPrice());
        assertEquals(new BigDecimal("600.00"), updatedOrder.getTotalPrice());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_items WHERE order_id = ?", Integer.class, orderId));
        assertEquals(OrderStatus.ACTIVE, updatedOrder.getStatus());
        verify(productClient, never()).reserveStock(any(StockReservationRequestDTO.class));
        verify(productClient, never()).releaseStock(any(StockReservationRequestDTO.class));
    }

    @Test
    void refreshActiveOrder_whenOrderIsLocked_thenWaitAndRefreshUpdatedQuantity() throws Exception
    {
        int userId = 1;
        int orderId = 2;

        when(productClient.getById(1)).thenReturn(ProductFactory.graphicCardDTO(10, 1500));

        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
        OrderEntity lockedOrder = orderRepository.findByUserIdAndStatusForUpdate(userId, OrderStatus.ACTIVE.name()).orElseThrow();
        int blockingPid = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);

        Future<Integer> request = executor.submit(() ->
        {
            return restTestClient.patch()
                    .uri("/active/refresh")
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        waitUntilOrderIsBlocked(blockingPid);
        assertFalse(request.isDone());

        OrderItemEntity lockedItem = orderItemRepository.findById(2).orElseThrow();
        lockedItem.setQuantity(3);
        lockedItem.setPrice(new BigDecimal("3000.00"));
        lockedOrder.setTotalPrice(new BigDecimal("3000.00"));
        transactionManager.commit(transaction);

        assertEquals(204, request.get(15, TimeUnit.SECONDS));

        OrderEntity updatedOrder = orderRepository.findById(orderId).orElseThrow();
        OrderItemEntity updatedItem = orderItemRepository.findById(2).orElseThrow();
        assertEquals(3, updatedItem.getQuantity());
        assertEquals(new BigDecimal("4500.00"), updatedItem.getPrice());
        assertEquals(new BigDecimal("4500.00"), updatedOrder.getTotalPrice());
        assertEquals(OrderStatus.ACTIVE, updatedOrder.getStatus());
        verify(productClient, never()).reserveStock(any(StockReservationRequestDTO.class));
        verify(productClient, never()).releaseStock(any(StockReservationRequestDTO.class));
    }

    @Test
    void clearActiveOrder_whenOrderIsLocked_thenWaitAndClearUpdatedOrder() throws Exception
    {
        int userId = 1;
        int orderId = 2;

        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
        OrderEntity lockedOrder = orderRepository.findByUserIdAndStatusForUpdate(userId, OrderStatus.ACTIVE.name()).orElseThrow();
        int blockingPid = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);

        Future<Integer> request = executor.submit(() ->
        {
            return restTestClient.patch()
                    .uri("/active/clear")
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        waitUntilOrderIsBlocked(blockingPid);
        assertFalse(request.isDone());

        OrderItemEntity lockedItem = orderItemRepository.findById(2).orElseThrow();
        lockedItem.setQuantity(3);
        lockedItem.setPrice(new BigDecimal("3000.00"));
        lockedOrder.setTotalPrice(new BigDecimal("3000.00"));
        transactionManager.commit(transaction);

        assertEquals(204, request.get(15, TimeUnit.SECONDS));

        OrderEntity updatedOrder = orderRepository.findById(orderId).orElseThrow();
        assertEquals(new BigDecimal("0.00"), updatedOrder.getTotalPrice());
        assertEquals(OrderStatus.ACTIVE, updatedOrder.getStatus());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_items WHERE order_id = ?", Integer.class, orderId));
        verify(productClient, never()).reserveStock(any(StockReservationRequestDTO.class));
        verify(productClient, never()).releaseStock(any(StockReservationRequestDTO.class));
    }

    @Test
    void checkout_whenOrderIsLocked_thenWaitAndReserveUpdatedQuantity() throws Exception
    {
        int userId = 1;
        int orderId = 2;

        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
        OrderEntity lockedOrder = orderRepository.findByUserIdAndStatusForUpdate(userId, OrderStatus.ACTIVE.name()).orElseThrow();
        int blockingPid = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);

        Future<Integer> request = executor.submit(() ->
        {
            return restTestClient.patch()
                    .uri("/active/checkout")
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        waitUntilOrderIsBlocked(blockingPid);
        assertFalse(request.isDone());

        OrderItemEntity lockedItem = orderItemRepository.findById(2).orElseThrow();
        lockedItem.setQuantity(3);
        lockedItem.setPrice(new BigDecimal("3000.00"));
        lockedOrder.setTotalPrice(new BigDecimal("3000.00"));
        transactionManager.commit(transaction);

        assertEquals(200, request.get(15, TimeUnit.SECONDS));

        OrderEntity updatedOrder = orderRepository.findById(orderId).orElseThrow();
        OrderItemEntity updatedItem = orderItemRepository.findById(2).orElseThrow();
        assertEquals(OrderStatus.PENDING, updatedOrder.getStatus());
        assertEquals(3, updatedItem.getQuantity());
        assertEquals(new BigDecimal("3000.00"), updatedOrder.getTotalPrice());
        verify(productClient, times(1)).reserveStock(new StockReservationRequestDTO(List.of(new ItemReservationDTO(1, 3))));
        verify(productClient, never()).reserveStock(new StockReservationRequestDTO(List.of(new ItemReservationDTO(1, 2))));
        verify(productClient, never()).releaseStock(any(StockReservationRequestDTO.class));
    }

    @Test
    void reopen_whenOrderIsLocked_thenWaitAndReleaseUpdatedQuantity() throws Exception
    {
        int userId = 1;
        int orderId = 2;

        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.PENDING);
        orderRepository.saveAndFlush(order);

        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
        OrderEntity lockedOrder = orderRepository.findByUserIdAndStatusForUpdate(userId, OrderStatus.PENDING.name()).orElseThrow();
        int blockingPid = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);

        Future<Integer> request = executor.submit(() ->
        {
            return restTestClient.patch()
                    .uri("/pending/reopen")
                    .exchange()
                    .expectBody()
                    .returnResult()
                    .getStatus()
                    .value();
        });

        waitUntilOrderIsBlocked(blockingPid);
        assertFalse(request.isDone());

        OrderItemEntity lockedItem = orderItemRepository.findById(2).orElseThrow();
        lockedItem.setQuantity(3);
        lockedItem.setPrice(new BigDecimal("3000.00"));
        lockedOrder.setTotalPrice(new BigDecimal("3000.00"));
        transactionManager.commit(transaction);

        assertEquals(200, request.get(15, TimeUnit.SECONDS));

        OrderEntity updatedOrder = orderRepository.findById(orderId).orElseThrow();
        OrderItemEntity updatedItem = orderItemRepository.findById(2).orElseThrow();
        assertEquals(OrderStatus.ACTIVE, updatedOrder.getStatus());
        assertEquals(3, updatedItem.getQuantity());
        assertEquals(new BigDecimal("3000.00"), updatedOrder.getTotalPrice());
        verify(productClient, times(1)).releaseStock(new StockReservationRequestDTO(List.of(new ItemReservationDTO(1, 3))));
        verify(productClient, never()).releaseStock(new StockReservationRequestDTO(List.of(new ItemReservationDTO(1, 2))));
        verify(productClient, never()).reserveStock(any(StockReservationRequestDTO.class));
    }

    @Test
    void complete_whenOrderIsLocked_thenWaitAndKeepUpdatedOrder() throws Exception
    {
        int orderId = 2;

        OrderEntity order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.PENDING);
        orderRepository.saveAndFlush(order);

        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
        OrderEntity lockedOrder = orderRepository.findByIdForUpdate(orderId).orElseThrow();
        int blockingPid = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);

        Future<Void> request = executor.submit(() ->
        {
            orderService.complete(orderId);
            return null;
        });

        waitUntilOrderIsBlocked(blockingPid);
        assertFalse(request.isDone());

        OrderItemEntity lockedItem = orderItemRepository.findById(2).orElseThrow();
        lockedItem.setQuantity(3);
        lockedItem.setPrice(new BigDecimal("3000.00"));
        lockedOrder.setTotalPrice(new BigDecimal("3000.00"));
        transactionManager.commit(transaction);

        request.get(15, TimeUnit.SECONDS);

        OrderEntity updatedOrder = orderRepository.findById(orderId).orElseThrow();
        OrderItemEntity updatedItem = orderItemRepository.findById(2).orElseThrow();
        assertEquals(OrderStatus.COMPLETED, updatedOrder.getStatus());
        assertEquals(3, updatedItem.getQuantity());
        assertEquals(new BigDecimal("3000.00"), updatedItem.getPrice());
        assertEquals(new BigDecimal("3000.00"), updatedOrder.getTotalPrice());
        verifyNoInteractions(productClient);
    }

    private void waitUntilOrderIsBlocked(int blockingPid)
    {
        await().atMost(10, TimeUnit.SECONDS).until(() -> jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM pg_stat_activity
                    WHERE ? = ANY(pg_blocking_pids(pid))
                    AND query ILIKE '%FROM orders%FOR UPDATE%'
                )
                """,
                Boolean.class,
                blockingPid
        ));
    }
}
