package com.dasigconnect.backend.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dasigconnect.backend.config.PaymongoProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymongoClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void createPaymentLink_sendsAuthenticatedRequestAndParsesResponse() {
        server.createContext("/payment_links", exchange -> respond(exchange, 201, """
                {"data":{"id":"link_1","url":"https://paymongo.test/link_1",
                "reference_number":"REF-1","amount":749900,"currency":"PHP",
                "status":"active","livemode":false}}
                """));

        PaymongoClient.PaymentLink result = client().createPaymentLink(
                749_900, "DASIGConnect DC-1", "Organization subscription invoice DC-1");

        assertThat(result.id()).isEqualTo("link_1");
        assertThat(result.amountCentavos()).isEqualTo(749_900);
        assertThat(result.liveMode()).isFalse();
        assertThat(authorization.get()).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("sk_test_example:".getBytes(StandardCharsets.UTF_8)));
        assertThat(requestBody.get()).contains(
                "\"amount\":749900", "\"currency\":\"PHP\"", "DASIGConnect DC-1");
    }

    @Test
    void retrievePaymentLink_parsesSettledPayments() {
        server.createContext("/payment_links/link_1", exchange -> respond(exchange, 200, """
                {"data":{"id":"link_1","url":"https://paymongo.test/link_1",
                "amount":749900,"currency":"PHP","status":"active","livemode":false}}
                """));
        server.createContext("/payment_links/link_1/payments", exchange -> respond(exchange, 200, """
                {"data":[{"payment_id":"pay_1","amount":749900,"currency":"PHP","status":"paid",
                "paid_at":"2026-10-18T12:00:00Z"}]}
                """));

        PaymongoClient.PaymentLink result = client().retrievePaymentLink("link_1");

        assertThat(result.status()).isEqualTo("active");
        assertThat(result.payments()).singleElement().satisfies(payment -> {
            assertThat(payment.id()).isEqualTo("pay_1");
            assertThat(payment.amountCentavos()).isEqualTo(749_900);
            assertThat(payment.currency()).isEqualTo("PHP");
        });
    }

    @Test
    void createRefund_sendsProviderContractAndParsesPendingRefund() {
        server.createContext("/refunds", exchange -> respond(exchange, 200, """
                {"data":{"id":"ref_1","attributes":{"amount":12500,"status":"pending",
                "reason":"requested_by_customer"}}}
                """));

        PaymongoClient.Refund result = client().createRefund(
                "pay_1", 12_500, "requested_by_customer", "approved");

        assertThat(result.id()).isEqualTo("ref_1");
        assertThat(result.amountCentavos()).isEqualTo(12_500);
        assertThat(requestBody.get()).contains(
                "\"payment_id\":\"pay_1\"", "\"amount\":12500", "\"notes\":\"approved\"");
    }

    @Test
    void providerError_exposesSanitizedProviderMessage() {
        server.createContext("/payment_links", exchange -> respond(exchange, 400, """
                {"errors":[{"detail":"Invalid amount"}]}
                """));

        assertThatThrownBy(() -> client().createPaymentLink(1, "test", "test"))
                .isInstanceOf(PaymongoApiException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("Invalid amount");
    }

    private PaymongoClient client() {
        PaymongoProperties properties = new PaymongoProperties(
                true, "TEST", baseUrl, "sk_test_example", "",
                "whsk_test_example", "", 300);
        return new PaymongoClient(properties, new ObjectMapper());
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
