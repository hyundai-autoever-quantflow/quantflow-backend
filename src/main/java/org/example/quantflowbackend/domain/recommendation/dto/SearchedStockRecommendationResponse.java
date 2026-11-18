package org.example.quantflowbackend.domain.recommendation.dto;

import org.example.quantflowbackend.domain.news.dto.PreparedNews;

import java.util.List;

public record SearchedStockRecommendationResponse(
        AiStockRecommendation.StockPick stock,
        String reason,
        List<Integer> newsNumbers,
        List<PreparedNews> news,
        AutoBuyStatus autoBuyStatus
) {
    public enum AutoBuyStatus {
        NO_RECOMMENDATION,
        NOT_ENABLED,
        NOT_IN_CATALOG,
        INACTIVE_IN_CATALOG,
        CATALOG_MATCH
    }
}
