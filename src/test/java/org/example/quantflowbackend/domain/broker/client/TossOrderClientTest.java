package org.example.quantflowbackend.domain.broker.client;

import org.example.quantflowbackend.domain.broker.client.TossOrderClient.BuyRequest;
import org.example.quantflowbackend.domain.broker.client.TossOrderClient.SubmissionOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TossOrderClientTest {
    private static final String ORDERS = "https://toss.test/api/v1/orders";
    private static final String KEY = "ab-2026-03-25-1";
    private static final BuyRequest BUY = BuyRequest.limit(
            KEY, "005930", BigDecimal.valueOf(2), BigDecimal.valueOf(30_000));

    private MockRestServiceServer server;
    private TossOrderClient client;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder().baseUrl("https://toss.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TossOrderClient(builder.build());
    }

    @Test
    void sendsAnAccountScopedIdempotentLimitBuy() {
        server.expect(requestTo(ORDERS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer fixture-token"))
                .andExpect(header("X-Tossinvest-Account", "42"))
                .andExpect(content().json("""
                        {"clientOrderId":"ab-2026-03-25-1","symbol":"005930","side":"BUY",
                         "orderType":"LIMIT","quantity":"2","price":"30000"}
                        """))
                .andRespond(withSuccess("""
                        {"result":{"clientOrderId":"ab-2026-03-25-1","orderId":"toss-order-1"}}
                        """, MediaType.APPLICATION_JSON));

        var result = client.submit("fixture-token", 42L, BUY);
        assertThat(result.outcome()).isEqualTo(SubmissionOutcome.ACCEPTED);
        assertThat(result.orderId()).isEqualTo("toss-order-1");
        server.verify();
    }

    @Test
    void separatesAConfirmedRejectionFromUncertainOutcomes() {
        server.expect(requestTo(ORDERS)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON).body("""
                        {"error":{"code":"insufficient-buying-power","message":"external error details"}}
                        """));
        server.expect(requestTo(ORDERS)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON).body("""
                        {"error":{"code":"idempotency-key-conflict"}}
                        """));
        server.expect(requestTo(ORDERS)).andRespond(withException(new SocketTimeoutException("fixture")));

        var rejected = client.submit("fixture-token", 42L, BUY);
        assertThat(rejected.outcome()).isEqualTo(SubmissionOutcome.REJECTED);
        assertThat(rejected.reason()).isEqualTo("insufficient-buying-power");
        assertThat(client.submit("fixture-token", 42L, BUY).outcome()).isEqualTo(SubmissionOutcome.UNKNOWN);
        assertThat(client.submit("fixture-token", 42L, BUY).outcome()).isEqualTo(SubmissionOutcome.UNKNOWN);
        server.verify();
    }

    @Test
    void readsFillQuantityByBrokerOrderId() {
        server.expect(requestTo(ORDERS + "/toss-order-1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Tossinvest-Account", "42"))
                .andRespond(withSuccess("""
                        {"result":{"orderId":"toss-order-1","symbol":"005930","side":"BUY","orderType":"LIMIT",
                        "status":"FILLED","currency":"KRW","quantity":"2","price":"30000",
                        "orderedAt":"2026-03-25T09:30:00+09:00","execution":{"filledQuantity":"2"}}}
                        """, MediaType.APPLICATION_JSON));

        var details = client.details("fixture-token", 42L, "toss-order-1");
        assertThat(details.status()).isEqualTo("FILLED");
        assertThat(details.execution().filledQuantity()).isEqualByComparingTo("2");
        server.verify();
    }

    @Test
    void readsTheApplicableDomesticCommissionForAnEligibleStock() {
        server.expect(requestTo("https://toss.test/api/v1/stocks?symbols=005930"))
                .andExpect(headerDoesNotExist("X-Tossinvest-Account"))
                .andRespond(withSuccess("""
                        {"result":[{"symbol":"005930","market":"KOSPI","currency":"KRW","status":"ACTIVE",
                        "securityType":"STOCK","isCommonShare":true,
                        "koreanMarketDetail":{"krxTradingSuspended":false,"liquidationTrading":false}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://toss.test/api/v1/commissions"))
                .andExpect(header("X-Tossinvest-Account", "42"))
                .andRespond(withSuccess("""
                        {"result":[{"marketCountry":"US","commissionRate":"0.002"},
                        {"marketCountry":"KR","commissionRate":"0.0005","endDate":"2026-03-24"},
                        {"marketCountry":"KR","commissionRate":"0.00015","startDate":"2026-03-25"}]}
                        """, MediaType.APPLICATION_JSON));

        var rate = client.domesticBuyCommission("fixture-token", 42L, "KOSPI", "005930", LocalDate.of(2026, 3, 25));
        assertThat(rate).isEqualByComparingTo("0.00015");
        server.verify();
    }
}
