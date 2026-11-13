package org.example.quantflowbackend.domain.broker.client;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.example.quantflowbackend.domain.broker.exception.TossAuthenticationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TossHoldingsClientTest {
    private MockRestServiceServer server;
    private TossHoldingsClient client;
    private ValidatorFactory validation;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://toss.test");
        server = MockRestServiceServer.bindTo(builder).build();
        validation = Validation.buildDefaultValidatorFactory();
        client = new TossHoldingsClient(builder.build(), validation.getValidator());
    }

    @AfterEach
    void cleanup() { validation.close(); }

    private String fixture(String name) throws Exception {
        return new ClassPathResource("toss/holdings-" + name + ".json").getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void sendsSelectedAccountHeaderAndPreservesCurrencyAndDecimalStrings() throws Exception {
        String payload = fixture("mixed").replace("\"quantity\": \"10\"", "\"quantity\": \"0.123456789012345678\"");
        server.expect(requestTo("https://toss.test/api/v1/holdings"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token"))
                .andExpect(header("X-Tossinvest-Account", "42"))
                .andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));
        var result = client.getHoldings("token", 42L, null);
        assertEquals(2, result.items().size());
        assertEquals("6500000", result.totalPurchaseAmount().krw().toPlainString());
        assertEquals("1553", result.totalPurchaseAmount().usd().toPlainString());
        assertEquals("0.123456789012345678", result.items().get(1).quantity().toPlainString());
        assertEquals("0.1077", result.items().get(0).profitLoss().rate().toPlainString());
        var json = new JsonMapper().readTree(new JsonMapper().writeValueAsString(result));
        assertTrue(json.get("items").get(1).get("quantity").isString());
        assertEquals("0.123456789012345678", json.get("items").get(1).get("quantity").asText());
        server.verify();
    }

    @Test
    void optionalSymbolIsForwardedAndNullableCurrencyRemainsNull() throws Exception {
        server.expect(requestTo("https://toss.test/api/v1/holdings?symbol=005930"))
                .andRespond(withSuccess(fixture("filtered"), MediaType.APPLICATION_JSON));
        var result = client.getHoldings("token", 1L, "005930");
        assertNull(result.totalPurchaseAmount().usd());
        assertEquals(1, result.items().size());
        server.verify();
    }

    @Test
    void emptyPortfolioReturnsEmptyListAndZeroSummary() throws Exception {
        server.expect(requestTo("https://toss.test/api/v1/holdings"))
                .andRespond(withSuccess(fixture("empty"), MediaType.APPLICATION_JSON));
        var result = client.getHoldings("token", 1L, null);
        assertTrue(result.items().isEmpty());
        assertEquals(0, result.totalPurchaseAmount().krw().signum());
        assertNull(result.totalPurchaseAmount().usd());
    }

    @Test
    void malformedEnvelopeAndMissingNestedFieldsAreRejected() {
        for (String payload : new String[]{"{}", "{\"result\":null}", "{\"result\":{\"items\":[]}}"}) {
            server.expect(requestTo("https://toss.test/api/v1/holdings"))
                    .andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));
        }
        for (int i = 0; i < 3; i++) {
            assertEquals(502, assertThrows(ResponseStatusException.class,
                    () -> client.getHoldings("token", 1L, null)).getStatusCode().value());
        }
    }

    @Test
    void invalidDecimalIsBadGatewayRatherThanSuccessfulOrServiceUnavailable() throws Exception {
        server.expect(requestTo("https://toss.test/api/v1/holdings"))
                .andRespond(withSuccess(fixture("mixed").replace("\"quantity\": \"100\"", "\"quantity\": \"invalid\""), MediaType.APPLICATION_JSON));
        assertEquals(502, assertThrows(ResponseStatusException.class,
                () -> client.getHoldings("token", 1L, null)).getStatusCode().value());
    }

    @Test
    void distinguishesUnauthorizedFromForbiddenAndSanitizesUpstreamError() {
        server.expect(requestTo("https://toss.test/api/v1/holdings"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("secret-token-do-not-expose"));
        server.expect(requestTo("https://toss.test/api/v1/holdings"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        var unauthorized = assertThrows(TossAuthenticationException.class, () -> client.getHoldings("token", 1L, null));
        assertEquals(422, unauthorized.getStatusCode().value());
        assertFalse(unauthorized.toString().contains("secret-token-do-not-expose"));
        assertNull(unauthorized.getCause());
        var forbidden = assertThrows(ResponseStatusException.class, () -> client.getHoldings("token", 1L, null));
        assertEquals(422, forbidden.getStatusCode().value());
        assertFalse(forbidden instanceof TossAuthenticationException);
    }

    @Test
    void rateLimitAndUpstreamFailureReturnServiceUnavailable() {
        for (HttpStatus status : new HttpStatus[]{HttpStatus.TOO_MANY_REQUESTS, HttpStatus.INTERNAL_SERVER_ERROR}) {
            server.expect(requestTo("https://toss.test/api/v1/holdings"))
                    .andRespond(withStatus(status));
        }
        for (int i = 0; i < 2; i++) {
            assertEquals(503, assertThrows(ResponseStatusException.class,
                    () -> client.getHoldings("token", 1L, null)).getStatusCode().value());
        }
    }

    @Test
    void readTimeoutReturnsServiceUnavailable() {
        server.expect(requestTo("https://toss.test/api/v1/holdings"))
                .andRespond(withException(new SocketTimeoutException("timeout")));
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> client.getHoldings("token", 1L, null)).getStatusCode().value());
    }
}
