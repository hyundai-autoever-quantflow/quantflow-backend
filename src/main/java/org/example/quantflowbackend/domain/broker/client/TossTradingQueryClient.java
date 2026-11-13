package org.example.quantflowbackend.domain.broker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.quantflowbackend.domain.broker.dto.BuyingPower;
import org.example.quantflowbackend.domain.broker.dto.StockPrice;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.example.quantflowbackend.domain.broker.client.TossApiErrors.*;

@Component
public class TossTradingQueryClient {
    private final RestClient client;
    private final Validator validator;

    public TossTradingQueryClient(@Qualifier("tossRestClient") RestClient client, Validator validator) {
        this.client = client;
        this.validator = validator;
    }

    public BuyingPower getBuyingPower(String accessToken, Long accountSeq, String currency) {
        try {
            BuyingPowerEnvelope response = client.get()
                    .uri(builder -> builder.path("/api/v1/buying-power").queryParam("currency", currency).build())
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        headers.set("X-Tossinvest-Account", accountSeq.toString());
                    })
                    .retrieve().body(BuyingPowerEnvelope.class);
            if (response == null || !validator.validate(response).isEmpty()
                    || !currency.equals(response.result().currency())) throw invalidResponse();
            return response.result();
        } catch (RestClientResponseException exception) {
            throw translateQuery(exception, "토스에서 조회할 계좌를 찾을 수 없습니다.");
        } catch (ResourceAccessException exception) {
            throw unavailable();
        } catch (RestClientException exception) {
            throw invalidResponse();
        }
    }

    public List<StockPrice> getPrices(String accessToken, List<String> symbols) {
        try {
            // Market prices require the token only, not the selected-account header.
            PricesEnvelope response = client.get()
                    .uri(builder -> builder.path("/api/v1/prices").queryParam("symbols", String.join(",", symbols)).build())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve().body(PricesEnvelope.class);
            if (response == null || !validator.validate(response).isEmpty()) throw invalidResponse();
            Set<String> requested = symbols.stream().map(s -> s.toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
            if (response.result().stream().anyMatch(price -> !requested.contains(price.symbol().toUpperCase(Locale.ROOT)))) {
                throw invalidResponse();
            }
            return response.result();
        } catch (RestClientResponseException exception) {
            throw translateQuery(exception, "토스에서 조회할 종목을 찾을 수 없습니다.");
        } catch (ResourceAccessException exception) {
            throw unavailable();
        } catch (RestClientException exception) {
            throw invalidResponse();
        }
    }

    private ResponseStatusException translateQuery(RestClientResponseException exception, String notFoundMessage) {
        if (exception.getStatusCode().value() == 400) return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "토스 조회 조건을 확인해주세요.");
        if (exception.getStatusCode().value() == 404) return new ResponseStatusException(HttpStatus.NOT_FOUND, notFoundMessage);
        return translate(exception, false);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BuyingPowerEnvelope(@NotNull @Valid BuyingPower result) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PricesEnvelope(@NotNull @Size(max = 200) @Valid List<@NotNull @Valid StockPrice> result) { }
}
