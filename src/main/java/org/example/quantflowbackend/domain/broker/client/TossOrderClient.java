package org.example.quantflowbackend.domain.broker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@Component
public class TossOrderClient {
    private static final Set<String> REJECTED_REQUESTS = Set.of("invalid-request", "confirm-high-value-required",
            "account-header-required");
    private static final Set<String> REJECTED_ORDERS = Set.of(
            "insufficient-buying-power", "order-hours-closed", "stock-restricted", "price-out-of-range",
            "order-type-not-allowed", "prerequisite-required", "market-not-supported-for-stock",
            "investor-exchange-not-integrated", "account-restricted", "max-order-amount-exceeded"
    );

    private final RestClient client;

    public TossOrderClient(@Qualifier("tossRestClient") RestClient client) {
        this.client = client;
    }

    public Submission submit(String token, Long accountSeq, BuyRequest request) {
        try {
            var response = client.post().uri("/api/v1/orders")
                    .headers(headers -> accountHeaders(headers, token, accountSeq))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve().body(SubmissionEnvelope.class);
            if (response == null || response.result() == null || !validOrderId(response.result().orderId())
                    || !request.clientOrderId().equals(response.result().clientOrderId())) {
                return Submission.unknown();
            }
            return new Submission(SubmissionOutcome.ACCEPTED, response.result().orderId(), null);
        } catch (RestClientResponseException exception) {
            var code = errorCode(exception);
            int status = exception.getStatusCode().value();
            if (code != null && ((status == 400 && REJECTED_REQUESTS.contains(code))
                    || (status == 422 && REJECTED_ORDERS.contains(code)))) {
                return new Submission(SubmissionOutcome.REJECTED, null, code);
            }
            return Submission.unknown();
        } catch (RestClientException exception) {
            return Submission.unknown();
        }
    }

    public OrderDetails details(String token, Long accountSeq, String orderId) {
        try {
            var response = client.get().uri("/api/v1/orders/{orderId}", orderId)
                    .headers(headers -> accountHeaders(headers, token, accountSeq))
                    .retrieve().body(OrderDetailsEnvelope.class);
            if (response == null || response.result() == null || !orderId.equals(response.result().orderId())) {
                throw TossApiErrors.invalidResponse();
            }
            return response.result();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "토스에서 주문을 확인할 수 없습니다.");
            }
            throw TossApiErrors.translate(exception, false);
        } catch (RestClientException exception) {
            throw TossApiErrors.unavailable();
        }
    }

    public BigDecimal domesticBuyCommission(String token, Long accountSeq, String market, String symbol, LocalDate date) {
        try {
            var stocks = client.get().uri(builder -> builder.path("/api/v1/stocks").queryParam("symbols", symbol).build())
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve().body(StockEnvelope.class);
            if (stocks == null || stocks.result() == null || stocks.result().size() != 1
                    || stocks.result().get(0) == null) {
                throw TossApiErrors.invalidResponse();
            }
            var stock = stocks.result().get(0);
            if (!symbol.equals(stock.symbol()) || !market.equals(stock.market()) || !"KRW".equals(stock.currency())
                    || !"ACTIVE".equals(stock.status()) || !"STOCK".equals(stock.securityType())
                    || !Boolean.TRUE.equals(stock.isCommonShare()) || stock.koreanMarketDetail() == null
                    || !Boolean.FALSE.equals(stock.koreanMarketDetail().krxTradingSuspended())
                    || !Boolean.FALSE.equals(stock.koreanMarketDetail().liquidationTrading())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "현재 국내 자동매수가 가능한 종목이 아닙니다.");
            }

            var commissions = client.get().uri("/api/v1/commissions")
                    .headers(headers -> accountHeaders(headers, token, accountSeq))
                    .retrieve().body(CommissionEnvelope.class);
            if (commissions == null || commissions.result() == null) {
                throw TossApiErrors.invalidResponse();
            }
            var applicable = commissions.result().stream()
                    .filter(value -> value != null && "KR".equals(value.marketCountry()))
                    .filter(value -> (value.startDate() == null || !date.isBefore(value.startDate()))
                            && (value.endDate() == null || !date.isAfter(value.endDate())))
                    .toList();
            if (applicable.isEmpty() || applicable.stream().anyMatch(value -> value.commissionRate() == null
                    || value.commissionRate().signum() < 0 || value.commissionRate().compareTo(BigDecimal.ONE) > 0)) {
                throw TossApiErrors.invalidResponse();
            }
            return applicable.stream().map(Commission::commissionRate).max(BigDecimal::compareTo).orElseThrow();
        } catch (RestClientResponseException exception) {
            throw TossApiErrors.translate(exception, false);
        } catch (RestClientException exception) {
            throw TossApiErrors.unavailable();
        }
    }

    private static void accountHeaders(HttpHeaders headers, String token, Long accountSeq) {
        headers.setBearerAuth(token);
        headers.set("X-Tossinvest-Account", accountSeq.toString());
    }

    private static boolean validOrderId(String value) {
        return value != null && !value.isBlank() && value.length() <= 100;
    }

    private static String errorCode(RestClientResponseException exception) {
        try {
            var response = exception.getResponseBodyAs(ErrorEnvelope.class);
            return response == null || response.error() == null ? null : response.error().code();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public record BuyRequest(String clientOrderId, String symbol, String side, String orderType, String quantity,
                             String price) {
        public static BuyRequest limit(String clientOrderId, String symbol, BigDecimal quantity, BigDecimal price) {
            return new BuyRequest(clientOrderId, symbol, "BUY", "LIMIT", quantity.toBigIntegerExact().toString(),
                    price.toBigIntegerExact().toString());
        }
    }

    public enum SubmissionOutcome { ACCEPTED, REJECTED, UNKNOWN }

    public record Submission(SubmissionOutcome outcome, String orderId, String reason) {
        public static Submission unknown() {
            return new Submission(SubmissionOutcome.UNKNOWN, null, "submission-unconfirmed");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderDetails(String orderId, String symbol, String side, String orderType, String status,
                               String currency, BigDecimal quantity, BigDecimal price, OffsetDateTime orderedAt,
                               FillDetails execution) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FillDetails(BigDecimal filledQuantity) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SubmissionEnvelope(SubmissionResult result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SubmissionResult(String orderId, String clientOrderId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrderDetailsEnvelope(OrderDetails result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorEnvelope(ErrorBody error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorBody(String code) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StockEnvelope(List<CurrentStock> result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CurrentStock(String symbol, String market, String currency, String status, String securityType,
                                Boolean isCommonShare, KoreanMarketDetail koreanMarketDetail) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KoreanMarketDetail(Boolean krxTradingSuspended, Boolean liquidationTrading) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CommissionEnvelope(List<Commission> result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Commission(String marketCountry, BigDecimal commissionRate, LocalDate startDate, LocalDate endDate) {}
}
