package org.example.quantflowbackend.domain.recommendation;

import jakarta.persistence.EntityManager;
import org.example.quantflowbackend.domain.broker.client.TossRankingClient;
import org.example.quantflowbackend.domain.broker.client.TossSectorClient;
import org.example.quantflowbackend.domain.broker.client.TossSectorClient.Sector;
import org.example.quantflowbackend.domain.broker.dto.TossInvestorRankingResponse;
import org.example.quantflowbackend.domain.broker.dto.TossMarketCalendarResponse;
import org.example.quantflowbackend.domain.broker.dto.TossMarketCalendarResponse.IntegratedSessions;
import org.example.quantflowbackend.domain.broker.dto.TossMarketCalendarResponse.MarketDay;
import org.example.quantflowbackend.domain.broker.dto.TossMarketCalendarResponse.RegularSession;
import org.example.quantflowbackend.domain.broker.dto.TossRankingRequest;
import org.example.quantflowbackend.domain.broker.dto.TossRankingResponse;
import org.example.quantflowbackend.domain.broker.service.TossTokenService;
import org.example.quantflowbackend.domain.news.client.NaverNewsClient;
import org.example.quantflowbackend.domain.news.dto.NaverNewsResponse;
import org.example.quantflowbackend.domain.recommendation.dto.AiStockRecommendation;
import org.example.quantflowbackend.domain.recommendation.dto.DailyRecommendation;
import org.example.quantflowbackend.domain.recommendation.dto.RankingSnapshot;
import org.example.quantflowbackend.domain.recommendation.repository.RankingSnapshotRepository;
import org.example.quantflowbackend.domain.stock.domain.Stock;
import org.example.quantflowbackend.domain.stock.repository.StockRepository;
import org.example.quantflowbackend.domain.user.domain.SocialProvider;
import org.example.quantflowbackend.domain.user.domain.User;
import org.example.quantflowbackend.domain.user.repository.UserRepository;
import org.example.quantflowbackend.global.ai.OpenAiResponseClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private static final String CATEGORY_PATH = RECOMMENDATION_PATH + "/category";
    private static final String PREFERENCE_PATH = "/api/v1/users/me/preferred-categories";
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
    @Autowired UserRepository users;
    @Autowired JdbcClient jdbc;
    @Autowired EntityManager entityManager;
    @MockitoBean TossRankingClient toss;
    @MockitoBean TossSectorClient sectors;
    @MockitoBean TossTokenService tokens;
    @MockitoBean NaverNewsClient naver;
    @MockitoBean OpenAiResponseClient ai;

    @AfterEach
    void deleteTestSnapshot() {
        redis.delete(List.of(SNAPSHOT_KEY, "quantflow:ranking:snapshot:" + LocalDate.now(SEOUL).minusDays(1)));
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

    @Test
    void listsCategoriesAndPersistsOptionalVersionedPreferences() throws Exception {
        stubSectors();
        var user = users.saveAndFlush(User.create(SocialProvider.GOOGLE, UUID.randomUUID().toString(), null, "회원"));
        var member = jwt().jwt(token -> token.subject(user.getMemberId().toString()));

        mvc.perform(get("/api/v1/categories").with(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].code").value("100"))
                .andExpect(jsonPath("$[1].name").value("반도체"))
                .andExpect(jsonPath("$[1].parentCode").value("10"));
        mvc.perform(get(PREFERENCE_PATH).with(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredCategories").isEmpty())
                .andExpect(jsonPath("$.preferencesVersion").value(0));
        mvc.perform(put(PREFERENCE_PATH).with(member).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferredCategories":["100","100"],"preferencesVersion":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredCategories.length()").value(1))
                .andExpect(jsonPath("$.preferencesVersion").value(1));
        entityManager.clear();
        mvc.perform(get(PREFERENCE_PATH).with(member))
                .andExpect(jsonPath("$.preferredCategories[0]").value("100"));

        mvc.perform(put(PREFERENCE_PATH).with(member).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferredCategories":["9999"],"preferencesVersion":1}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(put(PREFERENCE_PATH).with(member).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferredCategories":[],"preferencesVersion":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredCategories").isEmpty())
                .andExpect(jsonPath("$.preferencesVersion").value(2));
        mvc.perform(put(PREFERENCE_PATH).with(member).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"preferredCategories":["100"],"preferencesVersion":1}
                                """))
                .andExpect(status().isConflict());
        verify(sectors, times(1)).getSectors("test-token");
    }

    @Test
    void filtersTheSectorBeforeTheCandidateLimitAndDoesNotChangeTheDailyPick() throws Exception {
        stubSectors();
        when(sectors.getDomesticSymbols("test-token", "100")).thenReturn(List.of(SYMBOL));
        var today = LocalDate.now(SEOUL);
        var previousClose = stubCategoryCalendar(today);
        var symbols = new ArrayList<>(List.of("020001", "020002", "020003", "020004", "020005", "020006"));
        symbols.add(SYMBOL);
        var ranks = new ArrayList<TossRankingResponse.Ranking>();
        var buys = new ArrayList<TossInvestorRankingResponse.NetBuyItem>();
        for (var symbol : symbols) {
            int rank = ranks.size() + 1;
            ranks.add(new TossRankingResponse.Ranking(rank, symbol, BigDecimal.valueOf(100_000 - rank)));
            buys.add(new TossInvestorRankingResponse.NetBuyItem(rank, symbol, BigDecimal.valueOf(2_000_000)));
            var stock = Stock.create("KOSPI", symbol, "종목 " + symbol, null, "STOCK");
            stock.updateListingInfo(stock.getCompanyName(), "STOCK", true, null, previousClose.toLocalDateTime());
            stocks.save(stock);
        }
        stocks.flush();
        entityManager.clear();
        var investor = new TossInvestorRankingResponse.InvestorRanking(previousClose, buys);
        snapshots.save(new RankingSnapshot(today.minusDays(1), previousClose,
                new TossRankingResponse(previousClose, ranks), new TossInvestorRankingResponse(investor, investor)));
        seedDailyPick(today, "020001");
        var published = previousClose.plusMinutes(30).format(DateTimeFormatter.RFC_1123_DATE_TIME);
        when(naver.search("반도체", 100, 1, "date")).thenReturn(new NaverNewsResponse(published, 1, 1, 1,
                List.of(new NaverNewsResponse.Item("반도체 뉴스", "https://example.test/category", null, "종목 뉴스", published))));
        when(ai.generateStructured(anyString(), anyString(), eq(AiStockRecommendation.class))).thenAnswer(invocation -> {
            var candidates = new JsonMapper().readTree(invocation.getArgument(1, String.class)).get("candidates");
            assertEquals(1, candidates.size());
            assertEquals(SYMBOL, candidates.get(0).get("symbol").asText());
            return new AiStockRecommendation(Optional.of(new AiStockRecommendation.StockPick(
                    AiStockRecommendation.Market.KOSPI, SYMBOL, "응답 이름")), "카테고리 근거", List.of(1));
        });

        for (int request = 0; request < 2; request++) {
            mvc.perform(post(CATEGORY_PATH).with(jwt()).param("categoryCode", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.categoryCode").value("100"))
                    .andExpect(jsonPath("$.recommendation.stock.symbol").value(SYMBOL))
                    .andExpect(jsonPath("$.recommendation.stock.companyName").value("종목 " + SYMBOL));
        }
        verify(sectors, times(1)).getDomesticSymbols("test-token", "100");
        mvc.perform(post(CATEGORY_PATH).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryCode").doesNotExist())
                .andExpect(jsonPath("$.recommendation.stock.symbol").value("020001"));
        mvc.perform(get(RECOMMENDATION_PATH + "/today").with(jwt()))
                .andExpect(jsonPath("$.stock.symbol").value("020001"));
    }

    @Test
    void returnsCategoryHoldWithoutSendingAnEmptyCandidateListToAi() throws Exception {
        stubSectors();
        when(sectors.getDomesticSymbols("test-token", "100")).thenReturn(List.of());
        var today = LocalDate.now(SEOUL);
        var previousClose = stubCategoryCalendar(today);
        var emptyRanking = new TossInvestorRankingResponse.InvestorRanking(previousClose, List.of());
        snapshots.save(new RankingSnapshot(today.minusDays(1), previousClose,
                new TossRankingResponse(previousClose, List.of()),
                new TossInvestorRankingResponse(emptyRanking, emptyRanking)));

        mvc.perform(post(CATEGORY_PATH).with(jwt()).param("categoryCode", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendation.stock").doesNotExist())
                .andExpect(jsonPath("$.recommendation.reason").value("선택한 카테고리에 거래량과 순매수 조건을 충족하는 종목이 없습니다."));
        mvc.perform(post(CATEGORY_PATH).with(jwt()).param("categoryCode", "9999"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(naver, ai);
    }

    private void stubSectors() {
        when(tokens.getAccessToken("ranking-test-id", "ranking-test-secret")).thenReturn("test-token");
        when(sectors.getSectors("test-token")).thenReturn(List.of(
                new Sector("10", "IT 하드웨어", 0, null),
                new Sector("100", "반도체", 1, "10")
        ));
    }

    private OffsetDateTime stubCategoryCalendar(LocalDate today) {
        var previousClose = today.minusDays(1).atTime(15, 30).atZone(SEOUL).toOffsetDateTime();
        var previousOpen = previousClose.minusHours(6).minusMinutes(30);
        when(toss.getMarketCalendar("test-token", today)).thenReturn(new TossMarketCalendarResponse(
                new MarketDay(today, new IntegratedSessions(null)),
                new MarketDay(today.minusDays(1), new IntegratedSessions(new RegularSession(previousOpen, previousClose))),
                null
        ));
        return previousClose;
    }

    private void seedDailyPick(LocalDate today, String symbol) {
        var generatedAt = OffsetDateTime.now(SEOUL);
        var pick = new DailyRecommendation(today, today.minusDays(1), generatedAt,
                new AiStockRecommendation.StockPick(AiStockRecommendation.Market.KOSPI, symbol, "공통 추천"),
                "저장된 공통 추천", List.of(), List.of());
        jdbc.sql("""
                INSERT INTO daily_recommendations (trading_date, ranking_date, generated_at, result)
                VALUES (:date, :ranking, :generated, CAST(:result AS jsonb))
                """).param("date", today).param("ranking", today.minusDays(1)).param("generated", generatedAt)
                .param("result", new JsonMapper().writeValueAsString(pick)).update();
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
