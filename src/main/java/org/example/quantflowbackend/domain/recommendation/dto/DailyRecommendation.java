package org.example.quantflowbackend.domain.recommendation.dto;

import org.example.quantflowbackend.domain.news.dto.PreparedNews;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record DailyRecommendation(
            LocalDate tradingDate,
            LocalDate rankingDate,
            OffsetDateTime generatedAt,
            AiStockRecommendation.StockPick stock,
            String reason,
            List<Integer> newsNumbers,
            List<PreparedNews> news
    ) {}