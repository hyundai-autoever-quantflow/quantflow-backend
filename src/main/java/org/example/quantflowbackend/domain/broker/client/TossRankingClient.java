package org.example.quantflowbackend.domain.broker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import org.example.quantflowbackend.domain.broker.dto.TossInvestorRankingResponse;
import org.example.quantflowbackend.domain.broker.dto.TossMarketCalendarResponse;
import org.example.quantflowbackend.domain.broker.dto.TossRankingRequest;
import org.example.quantflowbackend.domain.broker.dto.TossRankingResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.List;

import static org.example.quantflowbackend.domain.broker.client.TossApiErrors.*;

@Component
public class TossRankingClient {
    private final RestClient client;
    private final Validator validator;

    public TossRankingClient(
            @Qualifier("tossRestClient") RestClient client, Validator validator
    ) {
        this.client = client;
        this.validator = validator;
    }

    public TossRankingResponse getRankings(String accessToken, TossRankingRequest request) {
        var query = client.get().uri(builder -> builder
                .path("/api/v1/rankings")
                .queryParam("type", request.type())
                .queryParam("marketCountry", request.marketCountry())
                .queryParam("duration", request.duration())
                .queryParam("count", 100)
                .build());
        return fetch(query, accessToken, RankingEnvelope.class).result();
    }

    public TossInvestorRankingResponse getInvestorRankings(String accessToken) {
        var query = client.get().uri(builder -> builder
                .path("/api/v1/rankings/investor-trading")
                .queryParam("count", 100)
                .build());
        return fetch(query, accessToken, InvestorRankingEnvelope.class).result();
    }

    public TossMarketCalendarResponse getMarketCalendar(String accessToken, LocalDate date) {
        var query = client.get().uri(builder -> builder
                .path("/api/v1/market-calendar/KR")
                .queryParam("date", date.toString())
                .build());

        return fetch(query, accessToken, MarketCalendarEnvelope.class).result();
    }

    private <T> T fetch(
            RestClient.RequestHeadersSpec<?> query, String accessToken, Class<T> bodyType
    ) {
        try {
            T body = query.headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(bodyType);
            if (body == null || !validator.validate(body).isEmpty()) {
                throw invalidResponse();
            }
            return body;
        } catch (RestClientResponseException exception) {
            throw translate(exception, false);
        } catch (ResourceAccessException exception) {
            throw unavailable();
        } catch (RestClientException exception) {
            throw invalidResponse();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RankingEnvelope(@NotNull @Valid TossRankingResponse result) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InvestorRankingEnvelope(@NotNull @Valid TossInvestorRankingResponse result) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MarketCalendarEnvelope(@NotNull @Valid TossMarketCalendarResponse result) {}
}

