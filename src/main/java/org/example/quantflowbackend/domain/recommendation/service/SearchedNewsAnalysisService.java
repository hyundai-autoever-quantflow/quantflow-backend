package org.example.quantflowbackend.domain.recommendation.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.automation.domain.AutomationDesiredState;
import org.example.quantflowbackend.domain.automation.repository.AutomationRepository;
import org.example.quantflowbackend.domain.news.dto.PreparedNews;
import org.example.quantflowbackend.domain.news.service.NewsPreparationService;
import org.example.quantflowbackend.domain.news.service.NewsService;
import org.example.quantflowbackend.domain.recommendation.dto.*;
import org.example.quantflowbackend.domain.stock.repository.StockRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.example.quantflowbackend.domain.recommendation.dto.AiStockRecommendation.StockPick;
import org.example.quantflowbackend.domain.recommendation.dto.SearchedStockRecommendationResponse.AutoBuyStatus;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchedNewsAnalysisService {
    private final NewsService newsService;
    private final NewsPreparationService newsPreparationService;
    private final NewsAnalysisService newsAnalysisService;
    private final AutomationRepository automationRepository;
    private final StockRepository stockRepository;
    private final RankingStockCandidateService rankingStockCandidateService;

    public SearchedNewsAnalysisResponse analyze(SearchedNewsAnalysisRequest request) {
        var search = preparedNews(request);
        String analysis = newsAnalysisService.analyze(search.news());

        return new SearchedNewsAnalysisResponse(
                request.query().strip(), search.fetchedCount(), search.news(), analysis
        );
    }

    public SearchedStockRecommendationResponse recommend(
            Long memberId, LocalDate rankingDate, SearchedNewsAnalysisRequest request
    ) {
        var draft = generateRecommendation(rankingDate, request);
        var recommendation = draft.recommendation();
        var stock = recommendation.stock().orElse(null);

        return new SearchedStockRecommendationResponse(
                stock,
                recommendation.reason(),
                recommendation.newsNumbers(),
                draft.news(),
                checkAutoBuyStatus(memberId, stock)
        );
    }

    public RecommendationDraft generateRecommendation(
            LocalDate rankingDate, SearchedNewsAnalysisRequest request
    ) {
        var search = preparedNews(request);

        List<RankedStockCandidate> candidates = search.news().isEmpty()
                ? List.of()
                : rankingStockCandidateService.load(rankingDate);

        return new RecommendationDraft(
                newsAnalysisService.recommend(search.news(), candidates),
                search.news()
        );
    }

    private AutoBuyStatus checkAutoBuyStatus(Long memberId, StockPick pick) {
        if (pick == null) {
            return AutoBuyStatus.NO_RECOMMENDATION;
        }

        boolean enabled =
                automationRepository.existsByUserMemberIdAndDesiredStateAndKillSwitchActiveFalse(
                        memberId, AutomationDesiredState.RUNNING
                );

        if (!enabled) {
            return AutoBuyStatus.NOT_ENABLED;
        }

        return stockRepository
                .findByMarketAndSymbol(pick.market().name(), pick.symbol())
                .filter(stock -> "TOSS".equals(stock.getMarketFlags().get("listingProvider")))
                .map(stock -> stock.isTradable()
                        ? AutoBuyStatus.CATALOG_MATCH
                        : AutoBuyStatus.INACTIVE_IN_CATALOG)
                .orElse(AutoBuyStatus.NOT_IN_CATALOG);
    }

    private PreparedSearch preparedNews(SearchedNewsAnalysisRequest request) {
        if (request.from() == null || request.to() == null
                || !request.from().isBefore(request.to())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "시작 시각은 종료 시각보다 이전이어야 합니다."
            );
        }
        var result = newsService.search(request.query(), 100, 1, "date");
        var news = newsPreparationService.prepare(
                result.items(), request.from(), request.to()
        );
        return new PreparedSearch(result.items().size(), news);
    }

    private record PreparedSearch(int fetchedCount, List<PreparedNews> news) {}

}
