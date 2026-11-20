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
public record TossInvestorRankingResponse(
        @NotNull @Valid InvestorRanking foreigner,
        @NotNull @Valid InvestorRanking institution
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InvestorRanking(
            OffsetDateTime rankedAt,
            @NotNull List<@NotNull @Valid NetBuyItem> netBuyRankings
            ) {

    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NetBuyItem(
            @Positive int rank,
            @NotBlank String symbol,
            @NotNull @Positive BigDecimal netBuyAmount
    ) {}
}
