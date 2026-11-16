package com.hardwarehub.payment_service.exception;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

public class GlobalExceptionHandlerTest
{
    @ParameterizedTest
    @MethodSource("paymentErrors")
    void handlePaymentException_whenThrown_thenReturnStructuredError(
            RuntimeException exception,
            int status,
            String error
    )
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test/errors");

        RestTestClient restTestClient = RestTestClient.bindTo(
                MockMvcBuilders.standaloneSetup(new ErrorController(exception))
                        .setControllerAdvice(new GlobalExceptionHandler(request))
                        .build()
        ).build();

        restTestClient.get()
                .uri("/test/errors")
                .exchange()
                .expectStatus().isEqualTo(status)
                .expectBody()
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.error").isEqualTo(error)
                .jsonPath("$.status").isEqualTo(status)
                .jsonPath("$.path").isEqualTo("/test/errors")
                .jsonPath("$.message").isEqualTo(exception.getMessage());
    }

    private static Stream<Arguments> paymentErrors()
    {
        return Stream.of(
                Arguments.of(new UnsupportedPaymentProviderException("Unsupported payment provider."),
                        400, "UNSUPPORTED_PAYMENT_PROVIDER"),
                Arguments.of(new InvalidPaymentRequestException("Invalid payment request."),
                        400, "INVALID_PAYMENT_REQUEST"),
                Arguments.of(new PaymentAlreadyCompletedException("The order has already been paid."),
                        409, "PAYMENT_ALREADY_COMPLETED"),
                Arguments.of(new InvalidPaymentStateException("The payment attempt cannot be safely canceled."),
                        409, "INVALID_PAYMENT_STATE"),
                Arguments.of(new PaymentProviderIntegrationException("The provider returned an incomplete order."),
                        502, "PAYMENT_PROVIDER_INTEGRATION"),
                Arguments.of(new PaymentProviderDataMismatchException("The paid amount does not match."),
                        502, "PAYMENT_PROVIDER_DATA_MISMATCH"),
                Arguments.of(new PaymentAttemptNotFoundException("Payment attempt not found."),
                        404, "PAYMENT_ATTEMPT_NOT_FOUND")
        );
    }

    @RestController
    static class ErrorController
    {
        private final RuntimeException exception;

        ErrorController(RuntimeException exception)
        {
            this.exception = exception;
        }

        @GetMapping("/test/errors")
        public void fail()
        {
            throw exception;
        }
    }
}
