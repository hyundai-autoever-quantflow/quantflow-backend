package org.example.quantflowbackend.domain.recommendation.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.recommendation.dto.DailyRecommendation;
import org.example.quantflowbackend.domain.recommendation.dto.SearchedNewsAnalysisRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DailyRecommendationService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RankingStockCandidateService rankingService;
    private final SearchedNewsAnalysisService newsAnalysisService;
    private final JdbcClient jdbc;
    private final JsonMapper json = new JsonMapper();

    public void generateToday(String query) {
        OffsetDateTime cutoff = OffsetDateTime.now(SEOUL);
        LocalDate today = cutoff.toLocalDate();

        if (find(today).isPresent()) {
            return;
        }

        var window = resolveWindow(cutoff).orElse(null);
        if (window == null) {
            return;
        }
        LocalDate rankingDate = window.rankingDate();
        var request = new SearchedNewsAnalysisRequest(query, window.from(), window.to());
        var draft = newsAnalysisService.generateRecommendation(rankingDate, request);
        var pick = draft.recommendation();

        var result = new DailyRecommendation(
                today, rankingDate, OffsetDateTime.now(SEOUL),
                pick.stock().orElse(null), pick.reason(), pick.newsNumbers(), draft.news()
        );

        jdbc.sql("""
                INSERT INTO daily_recommendations
                    (trading_date, ranking_date, generated_at, result)
                VALUES (:tradingDate, :rankingDate, :generatedAt, CAST(:result AS jsonb))
                ON CONFLICT (trading_date) DO NOTHING
                """)
                .param("tradingDate", today)
                .param("rankingDate", rankingDate)
                .param("generatedAt", result.generatedAt())
                .param("result", json.writeValueAsString(result))
                .update();

    }

    public Optional<DailyRecommendation> findToday() {
        return find(LocalDate.now(SEOUL));
    }

    private Optional<DailyRecommendation> find(LocalDate date) {
        return jdbc.sql("""
                        SELECT result::text FROM daily_recommendations WHERE trading_date = :date
                        """)
                .param("date", date)
                .query(String.class)
                .optional()
                .map(value -> json.readValue(value, DailyRecommendation.class));
    }

    Optional<RecommendationWindow> todayWindow() {
        return resolveWindow(OffsetDateTime.now(SEOUL));
    }

    private Optional<RecommendationWindow> resolveWindow(OffsetDateTime cutoff) {
        var today = cutoff.toLocalDate();
        var calendar = rankingService.marketCalendar(today);
        if (!today.equals(calendar.today().date())) {
            throw calendarUnavailable();
        }
        if (calendar.today().integrated() == null) {
            return Optional.empty();
        }

        var previous = calendar.previousBusinessDay();
        if (!previous.date().isBefore(today) || previous.integrated() == null
                || previous.integrated().regularMarket() == null) {
            throw calendarUnavailable();
        }
        return Optional.of(new RecommendationWindow(
                today, previous.date(), previous.integrated().regularMarket().endTime(), cutoff
        ));
    }

    record RecommendationWindow(LocalDate tradingDate, LocalDate rankingDate, OffsetDateTime from,
                                OffsetDateTime to) {}

    private ResponseStatusException calendarUnavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "국내 거래일을 확인할 수 없습니다."
        );
    }
}
