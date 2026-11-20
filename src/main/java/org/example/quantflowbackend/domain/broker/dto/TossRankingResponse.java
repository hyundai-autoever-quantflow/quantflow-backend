package org.example.quantflowbackend.domain.broker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossRankingResponse(
        OffsetDateTime rankedAt,
        @NotNull List<@NotNull @Valid Ranking> rankings
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ranking(
            @Positive int rank,
            @NotBlank String symbol,
            @NotNull BigDecimal tradingVolume
    ) {
    }
}
