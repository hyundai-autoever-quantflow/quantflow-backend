package org.example.quantflowbackend.domain.recommendation.dto;

import java.util.List;
import java.util.Optional;

public record AiStockRecommendation(
        Optional<StockPick> stock,
        String reason,
        List<Integer> newsNumbers
) {
    public record StockPick(
            Market market,
            String symbol,
            String companyName
    ) {}

    public enum Market {
        KOSPI,
        KOSDAQ
    }
}
