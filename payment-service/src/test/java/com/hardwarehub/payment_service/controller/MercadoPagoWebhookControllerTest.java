package com.hardwarehub.payment_service.controller;

import com.hardwarehub.payment_service.client.OrderClient;
import com.hardwarehub.payment_service.config.PostgresTestConfig;
import com.hardwarehub.payment_service.entity.MercadoPagoPaymentEntity;
import com.hardwarehub.payment_service.enums.PaymentStatus;
import com.hardwarehub.payment_service.enums.PaymentProvider;
import com.hardwarehub.payment_service.repository.MercadoPagoRepository;
import com.hardwarehub.payment_service.repository.PaymentRepository;
import com.hardwarehub.payment_service.service.MercadoPagoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@Sql(statements = {"DELETE FROM payments", "DELETE FROM mercado_pago_payments"})
@SpringBootTest(properties = "spring.config.location=classpath:/application-test.properties",
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MercadoPagoWebhookControllerTest
{
    @LocalServerPort
    private int port;

    @Autowired
    private MercadoPagoRepository mercadoPagoRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MercadoPagoService mercadoPagoService;

    @MockitoBean
    private OrderClient orderClient;

    @Value("${mercado-pago.webhook-secret}")
    private String webhookSecret;

    private RestTestClient restTestClient;
    private MockRestServiceServer server;
    private MercadoPagoPaymentEntity paymentAttempt;
    private String signature;

    @BeforeEach
    void setUp() throws Exception
    {
        restTestClient = RestTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/api/v1/payments/webhooks/mercado-pago")
                .build();

        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.mercadopago.com");
        server = MockRestServiceServer.bindTo(builder).build();

        // The service creates its own RestClient, so replace it with the test client.
        ReflectionTestUtils.setField(mercadoPagoService, "restClient", builder.build());

        paymentAttempt = mercadoPagoRepository.saveAndFlush(MercadoPagoPaymentEntity.builder()
                .orderId(10)
                .idempotencyKey("test-idempotency-key")
                .providerOrderId("mp-order-123")
                .amount(new BigDecimal("1500.00"))
                .currency("ARS")
                .status(PaymentStatus.PENDING)
                .build());

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String manifest = "id:mp-order-123;request-id:request-123;ts:" + timestamp + ";";

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String hash = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));

        signature = "ts=" + timestamp + ",v1=" + hash;
    }

    @Test
    void receive_whenSignatureIsInvalid_thenReturnUnauthorized()
    {
        String invalidSignature = signature.substring(0, signature.indexOf("v1="))
                + "v1=" + "0".repeat(64);

        restTestClient.post()
                .uri("?data.id=mp-order-123&type=order")
                .header("x-signature", invalidSignature)
                .header("x-request-id", "request-123")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void receive_whenSignatureIsMissing_thenReturnUnauthorized()
    {
        String requestId = "request-123";

        restTestClient.post()
                .uri("?data.id=mp-order-123&type=order")
                .header("x-request-id", requestId)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void receive_whenPaymentIsAccredited_thenReturnOk()
    {
        String responseBody = """
                {
                    "id": "mp-order-123",
                    "status": "processed",
                    "status_detail": "accredited",
                    "external_reference": "mp-attempt-%d",
                    "total_amount": "1500.00",
                    "total_paid_amount": "1500.00",
                    "currency": "ARS"
                }
                """.formatted(paymentAttempt.getId());

        server.expect(requestTo("https://api.mercadopago.com/v1/orders/mp-order-123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        restTestClient.post()
                .uri("?data.id=mp-order-123&type=order")
                .header("x-signature", signature)
                .header("x-request-id", "request-123")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void receive_whenProviderOrderIdIsUppercaseAndSignatureUsesLowercase_thenRegisterApprovedPayment()
            throws Exception
    {
        String providerOrderId = "ORDTST01TESTABC123";
        paymentAttempt.setProviderOrderId(providerOrderId);
        mercadoPagoRepository.saveAndFlush(paymentAttempt);

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String manifest = "id:ordtst01testabc123;request-id:request-123;ts:" + timestamp + ";";

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String hash = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        String lowercaseSignature = "ts=" + timestamp + ",v1=" + hash;

        String responseBody = """
                {
                    "id": "%s",
                    "status": "processed",
                    "status_detail": "accredited",
                    "external_reference": "mp-attempt-%d",
                    "total_amount": "1500.00",
                    "total_paid_amount": "1500.00",
                    "currency": "ARS"
                }
                """.formatted(providerOrderId, paymentAttempt.getId());

        server.expect(requestTo("https://api.mercadopago.com/v1/orders/" + providerOrderId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        restTestClient.post()
                .uri("?data.id=" + providerOrderId + "&type=order")
                .header("x-signature", lowercaseSignature)
                .header("x-request-id", "request-123")
                .exchange()
                .expectStatus().isOk();

        assertThat(mercadoPagoRepository.findById(paymentAttempt.getId()))
                .get().satisfies(attempt ->
                {
                    assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.APPROVED);
                    assertThat(attempt.getProviderOrderId()).isEqualTo(providerOrderId);
                });
        assertThat(paymentRepository.findAll()).singleElement().satisfies(payment ->
        {
            assertThat(payment.getOrderId()).isEqualTo(10);
            assertThat(payment.getProvider()).isEqualTo(PaymentProvider.MERCADO_PAGO);
            assertThat(payment.getProviderReference()).isEqualTo(providerOrderId);
            assertThat(payment.getAmount()).isEqualByComparingTo("1500.00");
            assertThat(payment.getCurrency()).isEqualTo("ARS");
        });
    }

    @Test
    void receive_whenNotificationIsRepeated_thenReturnOk()
    {
        String responseBody = """
                {
                    "id": "mp-order-123",
                    "status": "processed",
                    "status_detail": "accredited",
                    "external_reference": "mp-attempt-%d",
                    "total_amount": "1500.00",
                    "total_paid_amount": "1500.00",
                    "currency": "ARS"
                }
                """.formatted(paymentAttempt.getId());

        server.expect(requestTo("https://api.mercadopago.com/v1/orders/mp-order-123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.mercadopago.com/v1/orders/mp-order-123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        restTestClient.post()
                .uri("?data.id=mp-order-123&type=order")
                .header("x-signature", signature)
                .header("x-request-id", "request-123")
                .exchange()
                .expectStatus().isOk();

        restTestClient.post()
                .uri("?data.id=mp-order-123&type=order")
                .header("x-signature", signature)
                .header("x-request-id", "request-123")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void receive_whenOrderCompletionFails_thenKeepApprovedPayment()
    {
        String responseBody = """
                {
                    "id": "mp-order-123",
                    "status": "processed",
                    "status_detail": "accredited",
                    "external_reference": "mp-attempt-%d",
                    "total_amount": "1500.00",
                    "total_paid_amount": "1500.00",
                    "currency": "ARS"
                }
                """.formatted(paymentAttempt.getId());

        server.expect(requestTo("https://api.mercadopago.com/v1/orders/mp-order-123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        doThrow(new RestClientException("Order service unavailable."))
                .when(orderClient).completeOrder(10);

        restTestClient.post()
                .uri("?data.id=mp-order-123&type=order")
                .header("x-signature", signature)
                .header("x-request-id", "request-123")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isBetween(400, 599));

        assertThat(mercadoPagoRepository.findById(paymentAttempt.getId()))
                .get().extracting(MercadoPagoPaymentEntity::getStatus)
                .isEqualTo(PaymentStatus.APPROVED);
        assertThat(paymentRepository.findAll()).singleElement().satisfies(payment ->
        {
            assertThat(payment.getOrderId()).isEqualTo(10);
            assertThat(payment.getProvider()).isEqualTo(PaymentProvider.MERCADO_PAGO);
            assertThat(payment.getProviderReference()).isEqualTo("mp-order-123");
            assertThat(payment.getAmount()).isEqualByComparingTo("1500.00");
            assertThat(payment.getCurrency()).isEqualTo("ARS");
        });
    }

    @Test
    void receive_whenNotifiedAgainAfterOrderCompletionFails_thenReturnOkWithoutDuplicatingPayment()
    {
        String responseBody = """
                {
                    "id": "mp-order-123",
                    "status": "processed",
                    "status_detail": "accredited",
                    "external_reference": "mp-attempt-%d",
                    "total_amount": "1500.00",
                    "total_paid_amount": "1500.00",
                    "currency": "ARS"
                }
                """.formatted(paymentAttempt.getId());

        server.expect(requestTo("https://api.mercadopago.com/v1/orders/mp-order-123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.mercadopago.com/v1/orders/mp-order-123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        doThrow(new RestClientException("Order service unavailable."))
                .doNothing().when(orderClient).completeOrder(10);

        restTestClient.post()
                .uri("?data.id=mp-order-123&type=order")
                .header("x-signature", signature)
                .header("x-request-id", "request-123")
                .exchange()
                .expectStatus().value(status -> assertThat(status).isBetween(400, 599));

        Integer paymentId = paymentRepository.findAll().get(0).getId();

        restTestClient.post()
                .uri("?data.id=mp-order-123&type=order")
                .header("x-signature", signature)
                .header("x-request-id", "request-123")
                .exchange()
                .expectStatus().isOk();

        assertThat(mercadoPagoRepository.findById(paymentAttempt.getId()))
                .get().extracting(MercadoPagoPaymentEntity::getStatus)
                .isEqualTo(PaymentStatus.APPROVED);
        assertThat(paymentRepository.findAll()).singleElement().satisfies(payment ->
        {
            assertThat(payment.getId()).isEqualTo(paymentId);
            assertThat(payment.getOrderId()).isEqualTo(10);
            assertThat(payment.getProviderReference()).isEqualTo("mp-order-123");
        });
    }

    @Test
    void receive_whenPaymentIsStillProcessing_thenReturnOk()
    {
        String responseBody = """
                {
                    "id": "mp-order-123",
                    "status": "processing",
                    "status_detail": "in_process",
                    "external_reference": "mp-attempt-%d",
                    "total_amount": "1500.00",
                    "total_paid_amount": "0.00",
                    "currency": "ARS"
                }
                """.formatted(paymentAttempt.getId());

        server.expect(requestTo("https://api.mercadopago.com/v1/orders/mp-order-123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        restTestClient.post()
                .uri("?data.id=mp-order-123&type=order")
                .header("x-signature", signature)
                .header("x-request-id", "request-123")
                .exchange()
                .expectStatus().isOk();
    }
}
