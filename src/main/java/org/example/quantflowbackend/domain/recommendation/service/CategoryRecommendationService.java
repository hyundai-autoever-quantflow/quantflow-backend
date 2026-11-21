package org.example.quantflowbackend.domain.recommendation.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.recommendation.dto.DailyRecommendation;
import org.example.quantflowbackend.domain.recommendation.dto.SearchedNewsAnalysisRequest;
import org.example.quantflowbackend.domain.stock.service.StockCategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class CategoryRecommendationService {
    private final StockCategoryService categories;
    private final DailyRecommendationService dailyRecommendations;
    private final SearchedNewsAnalysisService analysis;

    public CategoryRecommendation recommend(String categoryCode) {
        if (categoryCode == null) {
            var daily = dailyRecommendations.findToday().orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "오늘 저장된 추천 결과가 없습니다."));
            return new CategoryRecommendation(null, null, daily);
        }

        var category = categories.require(categoryCode);
        var window = dailyRecommendations.todayWindow().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "오늘은 국내 시장 휴장일입니다."));
        categories.refreshMembership(category);

        var request = new SearchedNewsAnalysisRequest(category.name(), window.from(), window.to());
        var draft = analysis.generateCategoryRecommendation(window.rankingDate(), request, category.code());
        var pick = draft.recommendation();
        var recommendation = new DailyRecommendation(
                window.tradingDate(), window.rankingDate(), OffsetDateTime.now(window.to().getOffset()),
                pick.stock().orElse(null), pick.reason(), pick.newsNumbers(), draft.news()
        );
        return new CategoryRecommendation(category.code(), category.name(), recommendation);
    }

    public record CategoryRecommendation(String categoryCode, String categoryName, DailyRecommendation recommendation) {}
}
