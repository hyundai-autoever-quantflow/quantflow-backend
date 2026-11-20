package org.example.quantflowbackend.domain.broker.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossMarketCalendarResponse(
        @NotNull @Valid MarketDay today,
        @NotNull @Valid MarketDay previousBusinessDay,
        @Valid MarketDay nextBusinessDay
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MarketDay(
            @NotNull LocalDate date,
            @Valid IntegratedSessions integrated
            ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IntegratedSessions(@Valid RegularSession regularMarket) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RegularSession(@NotNull OffsetDateTime endTime) {}
}

