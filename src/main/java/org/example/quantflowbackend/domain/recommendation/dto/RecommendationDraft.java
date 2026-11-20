package org.example.quantflowbackend.domain.recommendation.dto;

import org.example.quantflowbackend.domain.news.dto.PreparedNews;

import java.util.List;

public record RecommendationDraft(
        AiStockRecommendation recommendation,
        List<PreparedNews> news
) {}