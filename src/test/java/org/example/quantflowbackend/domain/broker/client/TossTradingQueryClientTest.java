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
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TossTradingQueryClientTest {
    private MockRestServiceServer server;
    private TossTradingQueryClient client;
    private ValidatorFactory validation;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://toss.test");
        server = MockRestServiceServer.bindTo(builder).build();
        validation = Validation.buildDefaultValidatorFactory();
        client = new TossTradingQueryClient(builder.build(), validation.getValidator());
    }
    @AfterEach
    void cleanup() { validation.close(); }

    private String fixture(String file) throws Exception {
        return new ClassPathResource("toss/" + file + ".json").getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void buyingPowerUsesAccountHeaderAndCurrencyQuery() throws Exception {
        server.expect(requestTo("https://toss.test/api/v1/buying-power?currency=KRW"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token"))
                .andExpect(header("X-Tossinvest-Account", "42"))
                .andRespond(withSuccess(fixture("buying-power-krw"), MediaType.APPLICATION_JSON));
        var result = client.getBuyingPower("token", 42L, "KRW");
        assertEquals("KRW", result.currency());
        assertEquals("5000000", result.cashBuyingPower().toPlainString());
        var json = new JsonMapper().readTree(new JsonMapper().writeValueAsString(result));
        assertTrue(json.get("cashBuyingPower").isString());
        server.verify();
    }

    @Test
    void dollarAmountPreservesPrecisionAndZeroIsValid() {
        server.expect(requestTo("https://toss.test/api/v1/buying-power?currency=USD"))
                .andRespond(withSuccess("{\"result\":{\"currency\":\"USD\",\"cashBuyingPower\":\"3500.123456789012345678\"}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://toss.test/api/v1/buying-power?currency=KRW"))
                .andRespond(withSuccess("{\"result\":{\"currency\":\"KRW\",\"cashBuyingPower\":\"0\"}}", MediaType.APPLICATION_JSON));
        assertEquals("3500.123456789012345678", client.getBuyingPower("token", 1L, "USD").cashBuyingPower().toPlainString());
        assertEquals(0, client.getBuyingPower("token", 1L, "KRW").cashBuyingPower().signum());
    }

    @Test
    void pricesUseBatchQueryWithoutAccountHeaderAndParseTimestampOffsets() throws Exception {
        server.expect(requestTo("https://toss.test/api/v1/prices?symbols=005930,AAPL"))
                .andExpect(header("Authorization", "Bearer token"))
                .andExpect(headerDoesNotExist("X-Tossinvest-Account"))
                .andRespond(withSuccess(fixture("prices-mixed"), MediaType.APPLICATION_JSON));
        var result = client.getPrices("token", List.of("005930", "AAPL"));
        assertEquals(2, result.size());
        assertEquals("72000", result.get(0).lastPrice().toPlainString());
        assertEquals("USD", result.get(1).currency());
        assertEquals(Instant.parse("2026-03-25T00:30:00.123Z"), result.get(0).timestamp());
        var json = new JsonMapper().readTree(new JsonMapper().writeValueAsString(result));
        assertTrue(json.get(0).get("lastPrice").isString());
        server.verify();
    }

    @Test
    void nullTimestampFractionalPriceAndEmptyListAreSupported() {
        server.expect(requestTo("https://toss.test/api/v1/prices?symbols=AAPL"))
                .andRespond(withSuccess("{\"result\":[{\"symbol\":\"AAPL\",\"timestamp\":null,\"lastPrice\":\"0.123456789012345678\",\"currency\":\"USD\"}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://toss.test/api/v1/prices?symbols=AAPL"))
                .andRespond(withSuccess("{\"result\":[]}", MediaType.APPLICATION_JSON));
        var price = client.getPrices("token", List.of("AAPL")).get(0);
        assertNull(price.timestamp());
        assertEquals("0.123456789012345678", price.lastPrice().toPlainString());
        assertTrue(client.getPrices("token", List.of("AAPL")).isEmpty());
    }

    @Test
    void wrongReturnedCurrencyIsRejected() throws Exception {
        server.expect(requestTo("https://toss.test/api/v1/buying-power?currency=USD"))
                .andRespond(withSuccess(fixture("buying-power-krw"), MediaType.APPLICATION_JSON));
        assertEquals(502, assertThrows(ResponseStatusException.class,
                () -> client.getBuyingPower("token", 1L, "USD")).getStatusCode().value());
    }

    @Test
    void unrelatedReturnedStockIsRejected() throws Exception {
        server.expect(requestTo("https://toss.test/api/v1/prices?symbols=AAPL"))
                .andRespond(withSuccess(fixture("prices-kr"), MediaType.APPLICATION_JSON));
        assertEquals(502, assertThrows(ResponseStatusException.class,
                () -> client.getPrices("token", List.of("AAPL"))).getStatusCode().value());
    }

    @Test
    void missingRequiredFieldsAndInvalidNumberOrTimestampAreBadGateway() {
        List<String> payloads = List.of("{}", "{\"result\":null}",
                "{\"result\":[{\"symbol\":\"AAPL\",\"lastPrice\":\"1\"}]}",
                "{\"result\":[{\"symbol\":\"AAPL\",\"lastPrice\":\"invalid\",\"currency\":\"USD\"}]}",
                "{\"result\":[{\"symbol\":\"AAPL\",\"lastPrice\":\"1\",\"currency\":\"USD\",\"timestamp\":\"invalid\"}]}");
        for (String payload : payloads) server.expect(requestTo("https://toss.test/api/v1/prices?symbols=AAPL"))
                .andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));
        for (String ignored : payloads) assertEquals(502, assertThrows(ResponseStatusException.class,
                () -> client.getPrices("token", List.of("AAPL"))).getStatusCode().value());
    }

    @Test
    void upstreamErrorsAreMappedWithoutLeakingBodyAnd401RemainsDistinguishable() {
        List<HttpStatus> statuses = List.of(HttpStatus.BAD_REQUEST, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.NOT_FOUND, HttpStatus.TOO_MANY_REQUESTS, HttpStatus.INTERNAL_SERVER_ERROR);
        List<Integer> expected = List.of(400, 422, 422, 404, 503, 503);
        for (HttpStatus status : statuses) server.expect(requestTo("https://toss.test/api/v1/buying-power?currency=KRW"))
                .andRespond(withStatus(status).body("upstream-secret-do-not-leak"));
        for (int i = 0; i < statuses.size(); i++) {
            var error = assertThrows(ResponseStatusException.class, () -> client.getBuyingPower("token", 1L, "KRW"));
            assertEquals(expected.get(i), error.getStatusCode().value());
            assertEquals(statuses.get(i) == HttpStatus.UNAUTHORIZED, error instanceof TossAuthenticationException);
            assertFalse(error.toString().contains("upstream-secret-do-not-leak"));
            assertNull(error.getCause());
        }
    }

    @Test
    void timeoutsAreServiceUnavailable() {
        server.expect(requestTo("https://toss.test/api/v1/buying-power?currency=KRW"))
                .andRespond(withException(new SocketTimeoutException("timeout")));
        server.expect(requestTo("https://toss.test/api/v1/prices?symbols=AAPL"))
                .andRespond(withException(new SocketTimeoutException("timeout")));
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> client.getBuyingPower("token", 1L, "KRW")).getStatusCode().value());
        assertEquals(503, assertThrows(ResponseStatusException.class,
                () -> client.getPrices("token", List.of("AAPL"))).getStatusCode().value());
    }
}
