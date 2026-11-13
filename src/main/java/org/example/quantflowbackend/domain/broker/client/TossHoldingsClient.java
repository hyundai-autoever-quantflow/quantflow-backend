package org.example.quantflowbackend.domain.broker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import org.example.quantflowbackend.domain.broker.dto.HoldingsOverview;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

import static org.example.quantflowbackend.domain.broker.client.TossApiErrors.*;

@Component
public class TossHoldingsClient {
    private final RestClient client;
    private final Validator validator;

    public TossHoldingsClient(@Qualifier("tossRestClient") RestClient client, Validator validator) {
        this.client = client;
        this.validator = validator;
    }

    public HoldingsOverview getHoldings(String accessToken, Long accountSeq, String symbol) {
        try {
            Envelope response = client.get().uri(builder -> {
                        builder.path("/api/v1/holdings");
                        if (symbol != null) builder.queryParam("symbol", symbol);
                        return builder.build();
                    })
                    .headers(headers -> {
                        headers.setBearerAuth(accessToken);
                        headers.set("X-Tossinvest-Account", accountSeq.toString());
                    })
                    .retrieve().body(Envelope.class);
            if (response == null || !validator.validate(response).isEmpty()) throw invalidResponse();
            return response.result();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "토스에서 조회할 종목을 찾을 수 없습니다.");
            throw translate(exception, false);
        } catch (ResourceAccessException exception) {
            throw unavailable();
        } catch (RestClientException exception) {
            throw invalidResponse();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Envelope(@NotNull @Valid HoldingsOverview result) { }
}
