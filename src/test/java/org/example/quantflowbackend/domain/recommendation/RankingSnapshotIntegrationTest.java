package org.example.quantflowbackend.domain.recommendation;

import org.example.quantflowbackend.domain.broker.client.TossRankingClient;
import org.example.quantflowbackend.domain.broker.dto.TossInvestorRankingResponse;
import org.example.quantflowbackend.domain.broker.dto.TossRankingRequest;
import org.example.quantflowbackend.domain.broker.dto.TossRankingResponse;
import org.example.quantflowbackend.domain.broker.service.TossTokenService;
import org.example.quantflowbackend.domain.news.client.NaverNewsClient;
import org.example.quantflowbackend.domain.news.dto.NaverNewsResponse;
import org.example.quantflowbackend.domain.recommendation.dto.AiStockRecommendation;
import org.example.quantflowbackend.domain.recommendation.repository.RankingSnapshotRepository;
import org.example.quantflowbackend.domain.stock.domain.Stock;
import org.example.quantflowbackend.domain.stock.repository.StockRepository;
import org.example.quantflowbackend.global.ai.OpenAiResponseClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.stock-sync.client-id=ranking-test-id",
        "app.stock-sync.client-secret=ranking-test-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_TOSS_INTEGRATION_TESTS", matches = "true")
class RankingSnapshotIntegrationTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate RANKING_DATE = LocalDate.of(2025, 2, 3);
    private static final OffsetDateTime COLLECTED_AT =
            OffsetDateTime.parse("2025-02-03T21:05:00+09:00");
    private static final String SNAPSHOT_KEY = "quantflow:ranking:snapshot:" + RANKING_DATE;
    private static final String SYMBOL = "005930";
    private static final String CAPTURE_PATH = "/api/v1/ranking-snapshots";
    private static final String RECOMMENDATION_PATH = "/api/v1/analyses/recommendation";
    private static final String REQUEST_BODY = """
            {
              "query": "반도체",
              "from": "2025-02-03T15:30:00+09:00",
              "to": "2025-02-04T08:00:00+09:00"
            }
            """;

    @Autowired MockMvc mvc;
    @Autowired StockRepository stocks;
    @Autowired RankingSnapshotRepository snapshots;
    @Autowired StringRedisTemplate redis;
    @MockitoBean TossRankingClient toss;
    @MockitoBean TossTokenService tokens;
    @MockitoBean NaverNewsClient naver;
    @MockitoBean OpenAiResponseClient ai;

    @AfterEach
    void deleteTestSnapshot() {
        redis.delete(SNAPSHOT_KEY);
    }

    @Test
    void savesOneSnapshotAndUsesItForRecommendationWithoutRefetchingToss() throws Exception {
        setRankingResponses(COLLECTED_AT.minusMinutes(30));
        var stock = Stock.create("KOSPI", SYMBOL, "삼성전자", null, "STOCK");
        stock.updateListingInfo("삼성전자", "STOCK", true, null, COLLECTED_AT.toLocalDateTime());
        stocks.saveAndFlush(stock);
        setNewsResponse();
        when(ai.generateStructured(anyString(), anyString(), eq(AiStockRecommendation.class)))
                .thenReturn(new AiStockRecommendation(
                        Optional.of(new AiStockRecommendation.StockPick(
                                AiStockRecommendation.Market.KOSPI, SYMBOL, "삼성전자")),
                        "샘플 기사에 근거한 추천", List.of(1)
                ));

        try (var clock = mockStatic(OffsetDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(() -> OffsetDateTime.now(SEOUL)).thenReturn(COLLECTED_AT);
            mvc.perform(post(CAPTURE_PATH).with(jwt()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rankingDate").value(RANKING_DATE.toString()));
        }

        var saved = snapshots.find(RANKING_DATE).orElseThrow();
        assertEquals(COLLECTED_AT.toInstant(), saved.collectedAt().toInstant());
        assertEquals(SYMBOL, saved.volume().rankings().get(0).symbol());
        assertEquals(SYMBOL, saved.investor().foreigner().netBuyRankings().get(0).symbol());
        assertEquals(SYMBOL, saved.investor().institution().netBuyRankings().get(0).symbol());

        clearInvocations(toss, tokens);
        mvc.perform(post(RECOMMENDATION_PATH).with(jwt().jwt(jwt -> jwt.subject("1")))
                        .param("rankingDate", RANKING_DATE.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock.symbol").value(SYMBOL))
                .andExpect(jsonPath("$.autoBuyStatus").value("NOT_ENABLED"));
        verifyNoInteractions(toss, tokens);
    }

    @Test
    void rejectsMixedDatesAndReportsMissingSnapshotWithoutFetchingCurrentRankings() throws Exception {
        setRankingResponses(COLLECTED_AT.minusDays(1));
        setNewsResponse();

        try (var clock = mockStatic(OffsetDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(() -> OffsetDateTime.now(SEOUL)).thenReturn(COLLECTED_AT);
            mvc.perform(post(CAPTURE_PATH).with(jwt()))
                    .andExpect(status().isServiceUnavailable());
        }
        assertTrue(snapshots.find(RANKING_DATE).isEmpty());

        clearInvocations(toss, tokens);
        mvc.perform(post(RECOMMENDATION_PATH).with(jwt().jwt(jwt -> jwt.subject("1")))
                        .param("rankingDate", RANKING_DATE.toString())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(RANKING_DATE + "에 저장된 랭킹이 없습니다."));
        verifyNoInteractions(toss, tokens, ai);
    }

    private void setRankingResponses(OffsetDateTime institutionRankedAt) {
        var rankedAt = COLLECTED_AT.minusMinutes(35);
        var volume = new TossRankingResponse(rankedAt, List.of(
                new TossRankingResponse.Ranking(1, SYMBOL, BigDecimal.valueOf(3_000_000))
        ));
        var netBuys = List.of(
                new TossInvestorRankingResponse.NetBuyItem(1, SYMBOL, BigDecimal.valueOf(2_000_000))
        );
        var investor = new TossInvestorRankingResponse(
                new TossInvestorRankingResponse.InvestorRanking(rankedAt, netBuys),
                new TossInvestorRankingResponse.InvestorRanking(institutionRankedAt, netBuys)
        );
        when(tokens.getAccessToken("ranking-test-id", "ranking-test-secret")).thenReturn("test-token");
        when(toss.getRankings(eq("test-token"), any(TossRankingRequest.class))).thenReturn(volume);
        when(toss.getInvestorRankings("test-token")).thenReturn(investor);
    }

    private void setNewsResponse() {
        String pubDate = COLLECTED_AT.format(DateTimeFormatter.RFC_1123_DATE_TIME);
        var item = new NaverNewsResponse.Item(
                "반도체 산업 뉴스", "https://example.test/news", null, "반도체 기사 발췌", pubDate
        );
        when(naver.search("반도체", 100, 1, "date"))
                .thenReturn(new NaverNewsResponse(pubDate, 1, 1, 1, List.of(item)));
    }
}
