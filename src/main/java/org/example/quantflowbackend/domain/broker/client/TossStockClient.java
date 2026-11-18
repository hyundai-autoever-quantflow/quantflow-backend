package org.example.quantflowbackend.domain.broker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import org.example.quantflowbackend.domain.broker.dto.TossListedStock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;


import java.util.List;
import java.util.Set;

import static org.example.quantflowbackend.domain.broker.client.TossApiErrors.*;

@Component
public class TossStockClient {

    private static final Set<String> SUPPORTED_MARKETS = Set.of("KOSPI", "KOSDAQ");

    private final RestClient client;
    private final Validator validator;

    public TossStockClient(
            @Qualifier("tossRestClient") RestClient client,
            Validator validator
    ) {
        this.client = client;
        this.validator = validator;
    }

    public List<TossListedStock> getCommonStocks(
            String accessToken,
            String market
    ) {
        if (market == null || !SUPPORTED_MARKETS.contains(market)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "종목 동기화 시장은 KOSPI 또는 KOSDAQ이어야 합니다."
            );
        }
        try {
            StockListEnvelope response = client.get()
                    .uri(builder -> builder
                            .path("/api/v1/stocks/all")
                            .queryParam("market", market)
                            .queryParam("status", "ACTIVE")
                            .queryParam("securityType", "STOCK")
                            .queryParam("commonShare", true)
                            .build()
                    )
                    .headers(headers ->
                            headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(StockListEnvelope.class);

            if (response == null || !validator.validate(response).isEmpty()) {
                throw invalidResponse();
            }

            boolean unexpectedStock = response.result().stream()
                    .anyMatch(stock ->
                            !"STOCK".equals(stock.securityType())
                                    || !Boolean.TRUE.equals(stock.isCommonShare()));

            if (unexpectedStock) {
                throw invalidResponse();
            }

            return List.copyOf(response.result());
        } catch (RestClientResponseException exception) {
            throw translate(exception, false);
        } catch (ResourceAccessException exception) {
            throw unavailable();
        } catch (RestClientException exception) {
            throw invalidResponse();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StockListEnvelope(
            @NotNull
            @Valid
            List<@NotNull @Valid TossListedStock> result
    ) {}
}
