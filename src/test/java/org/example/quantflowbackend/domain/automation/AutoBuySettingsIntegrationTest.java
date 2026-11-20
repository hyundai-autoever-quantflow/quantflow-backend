package org.example.quantflowbackend.domain.automation;

import org.example.quantflowbackend.domain.broker.domain.BrokerConnection;
import org.example.quantflowbackend.domain.broker.domain.BrokerType;
import org.example.quantflowbackend.domain.broker.dto.BrokerBuyingPowerResponse;
import org.example.quantflowbackend.domain.broker.dto.BrokerPricesResponse;
import org.example.quantflowbackend.domain.broker.dto.BuyingPower;
import org.example.quantflowbackend.domain.broker.dto.StockPrice;
import org.example.quantflowbackend.domain.broker.dto.TossMarketCalendarResponse;
import org.example.quantflowbackend.domain.broker.dto.TossMarketCalendarResponse.IntegratedSessions;
import org.example.quantflowbackend.domain.broker.dto.TossMarketCalendarResponse.MarketDay;
import org.example.quantflowbackend.domain.broker.repository.BrokerConnectionRepository;
import org.example.quantflowbackend.domain.broker.service.BrokerTradingQueryService;
import org.example.quantflowbackend.domain.recommendation.dto.AiStockRecommendation.Market;
import org.example.quantflowbackend.domain.recommendation.dto.AiStockRecommendation.StockPick;
import org.example.quantflowbackend.domain.recommendation.dto.DailyRecommendation;
import org.example.quantflowbackend.domain.recommendation.service.RankingStockCandidateService;
import org.example.quantflowbackend.domain.stock.domain.Stock;
import org.example.quantflowbackend.domain.stock.repository.StockRepository;
import org.example.quantflowbackend.domain.user.domain.SocialProvider;
import org.example.quantflowbackend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_TOSS_INTEGRATION_TESTS", matches = "true")
class AutoBuySettingsIntegrationTest {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String ACCOUNT_PATH = "/api/v1/broker-connections/{connectionId}";
    private static final String SETTINGS = ACCOUNT_PATH + "/trading-settings";
    private static final String AUTOMATION = ACCOUNT_PATH + "/automation";
    private static final String SYMBOL = "005930";
    private static final String SETTINGS_BODY = """
            {"entryBudgetKrw":100000,"maxBuysPerDay":1,"dailyLossLimitKrw":10000}
            """;

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired BrokerConnectionRepository connections;
    @Autowired StockRepository stocks;
    @Autowired JdbcClient jdbc;
    @MockitoBean RankingStockCandidateService market;
    @MockitoBean BrokerTradingQueryService tradingQueries;

    private final JsonMapper json = new JsonMapper();

    @Test
    void registersAndEnablesSettingsThenPreviewsAndStops() throws Exception {
        var account = createAccount();
        var today = LocalDate.now(SEOUL);
        stubCalendar(today, false);

        var saved = createSettings(account, "settings-1", SETTINGS_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn();
        long settingsId = json.readTree(saved.getResponse().getContentAsString())
                .get("settingsId").asLong();

        createSettings(account, "settings-1", SETTINGS_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.settingsId").value(settingsId));
        verify(market, times(1)).marketCalendar(today);

        mvc.perform(get(SETTINGS + "/latest", account.getConnectionId())
                        .with(jwt().jwt(token -> token.subject(memberId(account).toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settingsId").value(settingsId));

        enable(account, Long.toString(settingsId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desiredState").value("RUNNING"))
                .andExpect(jsonPath("$.settings.status").value("ACTIVE"));

        seedRecommendation(today);
        var priceTime = Instant.parse("2026-01-01T00:00:00Z");
        when(tradingQueries.getBuyingPower(memberId(account), account.getConnectionId(), "KRW"))
                .thenReturn(new BrokerBuyingPowerResponse(account.getConnectionId().toString(), priceTime,
                        new BuyingPower("KRW", BigDecimal.valueOf(70_000))));
        when(tradingQueries.getPrices(memberId(account), account.getConnectionId(), SYMBOL))
                .thenReturn(new BrokerPricesResponse(account.getConnectionId().toString(), priceTime, List.of(
                        new StockPrice(SYMBOL, priceTime, BigDecimal.valueOf(30_000), "KRW")
                )));

        preview(account)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.settingsId").value(settingsId))
                .andExpect(jsonPath("$.symbol").value(SYMBOL))
                .andExpect(jsonPath("$.usableBudgetKrw").value(70_000))
                .andExpect(jsonPath("$.estimatedQuantity").value(2))
                .andExpect(jsonPath("$.estimatedAmountKrw").value(60_000));

        mvc.perform(post(AUTOMATION + "/stop", account.getConnectionId())
                        .with(jwt().jwt(token -> token.subject(memberId(account).toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desiredState").value("STOPPED"));
        clearInvocations(tradingQueries);
        preview(account).andExpect(status().isConflict());
        verifyNoInteractions(tradingQueries);
    }

    @Test
    void rejectsForeignOwnershipAndReusedKeysWithChangedSettings() throws Exception {
        var owner = createAccount();
        var other = createAccount();
        stubCalendar(LocalDate.now(SEOUL), false);
        var saved = createSettings(owner, "settings-1", SETTINGS_BODY)
                .andExpect(status().isCreated()).andReturn();
        long settingsId = json.readTree(saved.getResponse().getContentAsString())
                .get("settingsId").asLong();

        createSettings(owner, "settings-1", SETTINGS_BODY.replace("100000", "200000"))
                .andExpect(status().isConflict());
        mvc.perform(get(SETTINGS + "/latest", owner.getConnectionId())
                        .with(jwt().jwt(token -> token.subject(memberId(other).toString()))))
                .andExpect(status().isNotFound());
        mvc.perform(get(AUTOMATION + "/order-preview", owner.getConnectionId())
                        .with(jwt().jwt(token -> token.subject(memberId(other).toString()))))
                .andExpect(status().isNotFound());
        enable(other, Long.toString(settingsId)).andExpect(status().isNotFound());
        verifyNoInteractions(tradingQueries);
    }

    @Test
    void rejectsFractionalInputAndKeepsHolidaySettingsScheduledUntilTheirEffectiveDate() throws Exception {
        var account = createAccount();
        var today = LocalDate.now(SEOUL);
        stubCalendar(today, true);

        createSettings(account, "fractional", SETTINGS_BODY.replace("\"maxBuysPerDay\":1", "\"maxBuysPerDay\":1.5"))
                .andExpect(status().isBadRequest());
        enable(account, "1.5").andExpect(status().isBadRequest());
        verifyNoInteractions(market);

        var saved = createSettings(account, "scheduled", SETTINGS_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.effectiveTradingDate").value(today.plusDays(1).toString()))
                .andReturn();
        long settingsId = json.readTree(saved.getResponse().getContentAsString())
                .get("settingsId").asLong();
        enable(account, Long.toString(settingsId)).andExpect(status().isConflict());
        verifyNoInteractions(tradingQueries);
    }

    private BrokerConnection createAccount() {
        String fixture = UUID.randomUUID().toString();
        var user = users.saveAndFlush(org.example.quantflowbackend.domain.user.domain.User
                .create(SocialProvider.GOOGLE, fixture, null, "테스트 회원"));
        var account = BrokerConnection.create(
                user, BrokerType.TOSS, fixture, "123****", fixture, "account-" + fixture, "fixture"
        );
        account.completeTossVerification(fixture, OffsetDateTime.now(SEOUL).toLocalDateTime());
        return connections.saveAndFlush(account);
    }

    private void stubCalendar(LocalDate today, boolean holiday) {
        var open = new IntegratedSessions(null);
        when(market.marketCalendar(today)).thenReturn(new TossMarketCalendarResponse(
                new MarketDay(today, holiday ? null : open),
                new MarketDay(today.minusDays(1), open),
                new MarketDay(today.plusDays(1), open)
        ));
    }

    private void seedRecommendation(LocalDate today) {
        var now = OffsetDateTime.now(SEOUL);
        var stock = Stock.create("KOSPI", SYMBOL, "테스트 종목", null, "STOCK");
        stock.updateListingInfo("테스트 종목", "STOCK", true, null, now.toLocalDateTime());
        stocks.saveAndFlush(stock);

        var recommendation = new DailyRecommendation(
                today, today.minusDays(1), now, new StockPick(Market.KOSPI, SYMBOL, "테스트 종목"),
                "샘플 추천", List.of(), List.of()
        );
        jdbc.sql("""
                INSERT INTO daily_recommendations (trading_date, ranking_date, generated_at, result)
                VALUES (:today, :previous, :now, CAST(:payload AS jsonb))
                """)
                .param("today", today)
                .param("previous", today.minusDays(1))
                .param("now", now)
                .param("payload", json.writeValueAsString(recommendation))
                .update();
    }

    private ResultActions createSettings(BrokerConnection account, String key, String body) throws Exception {
        return mvc.perform(post(SETTINGS, account.getConnectionId())
                .with(jwt().jwt(token -> token.subject(memberId(account).toString())))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions enable(BrokerConnection account, String settingsId) throws Exception {
        return mvc.perform(post(AUTOMATION + "/enable", account.getConnectionId())
                .with(jwt().jwt(token -> token.subject(memberId(account).toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settingsId\":" + settingsId + "}"));
    }

    private ResultActions preview(BrokerConnection account) throws Exception {
        return mvc.perform(get(AUTOMATION + "/order-preview", account.getConnectionId())
                .with(jwt().jwt(token -> token.subject(memberId(account).toString()))));
    }

    private Long memberId(BrokerConnection account) {
        return account.getUser().getMemberId();
    }
}
