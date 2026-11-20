package org.example.quantflowbackend.domain.automation;

import org.example.quantflowbackend.domain.broker.client.TossOrderClient;
import org.example.quantflowbackend.domain.broker.client.TossOrderClient.FillDetails;
import org.example.quantflowbackend.domain.broker.client.TossOrderClient.OrderDetails;
import org.example.quantflowbackend.domain.broker.client.TossOrderClient.Submission;
import org.example.quantflowbackend.domain.broker.client.TossOrderClient.SubmissionOutcome;
import org.example.quantflowbackend.domain.broker.domain.BrokerConnection;
import org.example.quantflowbackend.domain.broker.domain.BrokerType;
import org.example.quantflowbackend.domain.broker.dto.BrokerBuyingPowerResponse;
import org.example.quantflowbackend.domain.broker.dto.BrokerCredentialData;
import org.example.quantflowbackend.domain.broker.dto.BrokerPricesResponse;
import org.example.quantflowbackend.domain.broker.dto.BuyingPower;
import org.example.quantflowbackend.domain.broker.dto.StockPrice;
import org.example.quantflowbackend.domain.broker.dto.TossMarketCalendarResponse;
import org.example.quantflowbackend.domain.broker.dto.TossMarketCalendarResponse.IntegratedSessions;
import org.example.quantflowbackend.domain.broker.dto.TossMarketCalendarResponse.MarketDay;
import org.example.quantflowbackend.domain.broker.dto.TossMarketCalendarResponse.RegularSession;
import org.example.quantflowbackend.domain.broker.repository.BrokerConnectionRepository;
import org.example.quantflowbackend.domain.broker.service.BrokerAccountAccessService;
import org.example.quantflowbackend.domain.broker.service.BrokerTradingQueryService;
import org.example.quantflowbackend.domain.broker.service.TossTokenService;
import org.example.quantflowbackend.domain.recommendation.dto.AiStockRecommendation.Market;
import org.example.quantflowbackend.domain.recommendation.dto.AiStockRecommendation.StockPick;
import org.example.quantflowbackend.domain.recommendation.dto.DailyRecommendation;
import org.example.quantflowbackend.domain.recommendation.service.RankingStockCandidateService;
import org.example.quantflowbackend.domain.stock.domain.Stock;
import org.example.quantflowbackend.domain.stock.repository.StockRepository;
import org.example.quantflowbackend.domain.trading.domain.OrderSide;
import org.example.quantflowbackend.domain.trading.domain.OrderStatus;
import org.example.quantflowbackend.domain.trading.domain.OrderType;
import org.example.quantflowbackend.domain.trading.repository.OrderRepository;
import org.example.quantflowbackend.domain.trading.service.AutoBuyOrderExecutionService;
import org.example.quantflowbackend.domain.trading.service.AutoBuyOrderPreparationService;
import org.example.quantflowbackend.domain.user.domain.SocialProvider;
import org.example.quantflowbackend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Autowired OrderRepository orders;
    @Autowired AutoBuyOrderPreparationService orderPreparation;
    @Autowired AutoBuyOrderExecutionService orderExecution;
    @MockitoBean RankingStockCandidateService market;
    @MockitoBean BrokerTradingQueryService tradingQueries;
    @MockitoBean Clock clock;
    @MockitoBean BrokerAccountAccessService accountAccess;
    @MockitoBean TossTokenService tossTokens;
    @MockitoBean TossOrderClient tossOrders;

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

    @Test
    void preparesOneLimitOrderForTodayAndRejectsDuplicatePreparation() throws Exception {
        var fixture = setupOrderPreparation(LocalTime.of(10, 0), 5, 70_000);
        seedRisk(fixture, 5, 0, 0);
        var account = fixture.account();

        Long orderId = orderPreparation.prepare(memberId(account), account.getConnectionId());

        var order = orders.findById(orderId).orElseThrow();
        assertThat(order.getRecommendationTradingDate()).isEqualTo(fixture.now().toLocalDate());
        assertThat(order.getConnection().getConnectionId()).isEqualTo(account.getConnectionId());
        assertThat(order.getSettings().getSettingsId()).isEqualTo(fixture.settingsId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(order.getOrderType()).isEqualTo(OrderType.LIMIT);
        assertThat(order.getQuantity()).isEqualByComparingTo("2");
        assertThat(order.getLimitPrice()).isEqualByComparingTo("30000");
        assertThat(order.getExpiresAt()).isEqualTo(fixture.now().plusSeconds(25).toLocalDateTime());

        assertThatThrownBy(() -> orderPreparation.prepare(memberId(account), account.getConnectionId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("자동매수 주문이 이미 존재합니다");
        assertThat(countOrders(account)).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
            "16:00, 5, 5, 0, 0, 70000, 정규장 거래 시간",
            "10:00, 31, 5, 0, 0, 70000, 정규장의 최신 시세",
            "10:00, 5, 61, 0, 0, 70000, 최신 계좌 손익",
            "10:00, 5, 5, , 0, 70000, 오늘의 계좌 손익",
            "10:00, 5, 5, -10000, 0, 70000, 일일 손실 한도",
            "10:00, 5, 5, 0, 1, 70000, 일일 최대 매수 횟수",
            "10:00, 5, 5, 0, 0, 30000, 한 주를 주문할 수 없습니다"
    })
    void keepsInvalidOrdersOutOfTheDatabase(
            LocalTime time, long quoteAge, long riskAge, Long dailyPnl, int buyCount, long cash, String reason
    ) throws Exception {
        var fixture = setupOrderPreparation(time, quoteAge, cash);
        if (dailyPnl != null) {
            seedRisk(fixture, riskAge, dailyPnl, buyCount);
        }
        var account = fixture.account();

        assertThatThrownBy(() -> orderPreparation.prepare(memberId(account), account.getConnectionId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(reason);
        assertThat(countOrders(account)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"ACCEPTED, ACCEPTED", "REJECTED, REJECTED", "UNKNOWN, UNKNOWN"})
    void submitsOnlyOnceAndReconcilesKnownBrokerOrders(SubmissionOutcome outcome, OrderStatus expected) throws Exception {
        var fixture = setupOrderPreparation(LocalTime.of(10, 0), 5, 70_000);
        seedRisk(fixture, 5, 0, 0);
        var account = fixture.account();
        Long memberId = memberId(account);
        Long connectionId = account.getConnectionId();
        String brokerId = "broker-order-fixture";
        when(tossOrders.submit(eq("fixture-token"), eq(42L), any())).thenAnswer(invocation -> {
            assertThat(jdbc.sql("SELECT status FROM orders WHERE connection_id = :connection")
                    .param("connection", connectionId).query(String.class).single()).isEqualTo("SUBMITTING");
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager
                    .isActualTransactionActive()).isFalse();
            return switch (outcome) {
                case ACCEPTED -> new Submission(outcome, brokerId, null);
                case REJECTED -> new Submission(outcome, null, "insufficient-buying-power");
                case UNKNOWN -> throw new org.springframework.web.client.ResourceAccessException("simulated timeout");
            };
        });
        TestTransaction.flagForCommit();
        TestTransaction.end();

        try {
            var order = orderExecution.executeToday(memberId, connectionId);
            assertThat(order.status()).isEqualTo(expected);
            assertThatThrownBy(() -> orderExecution.submitPrepared(memberId, connectionId, order.orderId()))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("이미 전송을 시도했습니다");
            assertThat(orderExecution.executeToday(memberId, connectionId).orderId()).isEqualTo(order.orderId());
            verify(tossOrders, times(1)).submit(eq("fixture-token"), eq(42L), any());
            mvc.perform(get(AUTOMATION + "/orders/today", connectionId)
                            .with(jwt().jwt(token -> token.subject(memberId.toString()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(expected.name()));
            mvc.perform(get(AUTOMATION + "/orders/today", connectionId)
                            .with(jwt().jwt(token -> token.subject("987654321"))))
                    .andExpect(status().isNotFound());

            if (expected == OrderStatus.ACCEPTED) {
                assertThat(order.brokerOrderId()).isEqualTo(brokerId);
                when(tossOrders.details("fixture-token", 42L, brokerId))
                        .thenReturn(new OrderDetails(brokerId, SYMBOL, "BUY", "LIMIT", "PARTIAL_FILLED", "KRW",
                                        BigDecimal.valueOf(2), BigDecimal.valueOf(30_000), fixture.now(),
                                        new FillDetails(BigDecimal.ONE)),
                                new OrderDetails(brokerId, SYMBOL, "BUY", "LIMIT", "FILLED", "KRW",
                                        BigDecimal.valueOf(2), BigDecimal.valueOf(30_000), fixture.now(),
                                        new FillDetails(BigDecimal.valueOf(2))));
                assertThat(orderExecution.refresh(memberId, connectionId, order.orderId()).status())
                        .isEqualTo(OrderStatus.PARTIALLY_FILLED);
                var filled = orderExecution.refresh(memberId, connectionId, order.orderId());
                assertThat(filled.status()).isEqualTo(OrderStatus.FILLED);
                assertThat(filled.filledQuantity()).isEqualByComparingTo("2");
                assertThat(jdbc.sql("SELECT count(*) FROM order_events WHERE order_id = :orderId")
                        .param("orderId", order.orderId()).query(Long.class).single()).isEqualTo(4);
            } else {
                assertThat(orderExecution.refresh(memberId, connectionId, order.orderId()).status()).isEqualTo(expected);
                verify(tossOrders, never()).details(any(), any(), any());
            }
        } finally {
            TestTransaction.start();
            cleanupCommittedOrderFixture(fixture);
            TestTransaction.flagForCommit();
            TestTransaction.end();
        }
    }

    private void cleanupCommittedOrderFixture(PreparationFixture fixture) {
        Long connectionId = fixture.account().getConnectionId();
        Long userId = memberId(fixture.account());
        jdbc.sql("""
                DELETE FROM order_events WHERE order_id IN (
                    SELECT order_id FROM orders WHERE connection_id = :connectionId
                )
                """).param("connectionId", connectionId).update();
        jdbc.sql("DELETE FROM orders WHERE connection_id = :connectionId").param("connectionId", connectionId).update();
        jdbc.sql("DELETE FROM daily_risk_states WHERE connection_id = :connectionId")
                .param("connectionId", connectionId).update();
        jdbc.sql("DELETE FROM automations WHERE connection_id = :connectionId")
                .param("connectionId", connectionId).update();
        jdbc.sql("DELETE FROM trading_settings WHERE settings_id = :settingsId")
                .param("settingsId", fixture.settingsId()).update();
        jdbc.sql("DELETE FROM broker_connections WHERE connection_id = :connectionId")
                .param("connectionId", connectionId).update();
        jdbc.sql("DELETE FROM users WHERE member_id = :userId").param("userId", userId).update();
        jdbc.sql("DELETE FROM daily_recommendations WHERE trading_date = :date")
                .param("date", fixture.now().toLocalDate()).update();
        jdbc.sql("DELETE FROM stocks WHERE market = 'KOSPI' AND symbol = :symbol").param("symbol", SYMBOL).update();
    }

    private PreparationFixture setupOrderPreparation(LocalTime time, long quoteAge, long cash) throws Exception {
        var account = createAccount();
        var today = LocalDate.now(SEOUL);
        var now = today.atTime(time).atZone(SEOUL).toOffsetDateTime();
        var regular = new RegularSession(
                today.atTime(9, 0).atZone(SEOUL).toOffsetDateTime(),
                today.atTime(15, 30).atZone(SEOUL).toOffsetDateTime()
        );
        var open = new IntegratedSessions(regular);
        when(clock.instant()).thenReturn(now.toInstant());
        when(market.marketCalendar(today)).thenReturn(new TossMarketCalendarResponse(
                new MarketDay(today, open), new MarketDay(today.minusDays(1), open), null
        ));

        var saved = createSettings(account, "prepare", SETTINGS_BODY)
                .andExpect(status().isCreated()).andReturn();
        long settingsId = json.readTree(saved.getResponse().getContentAsString()).get("settingsId").asLong();
        enable(account, Long.toString(settingsId)).andExpect(status().isOk());
        seedRecommendation(today);
        when(accountAccess.load(memberId(account), account.getConnectionId()))
                .thenReturn(new BrokerCredentialData("fixture-client", "fixture-secret", 42L));
        when(tossTokens.getAccessToken("fixture-client", "fixture-secret")).thenReturn("fixture-token");
        when(tossOrders.domesticBuyCommission("fixture-token", 42L, "KOSPI", SYMBOL, today))
                .thenReturn(new BigDecimal("0.001"));

        var quoteTime = now.minusSeconds(quoteAge).toInstant();
        when(tradingQueries.getBuyingPower(memberId(account), account.getConnectionId(), "KRW"))
                .thenReturn(new BrokerBuyingPowerResponse(account.getConnectionId().toString(), now.toInstant(),
                        new BuyingPower("KRW", BigDecimal.valueOf(cash))));
        when(tradingQueries.getPrices(memberId(account), account.getConnectionId(), SYMBOL))
                .thenReturn(new BrokerPricesResponse(account.getConnectionId().toString(), now.toInstant(), List.of(
                        new StockPrice(SYMBOL, quoteTime, BigDecimal.valueOf(30_000), "KRW")
                )));
        return new PreparationFixture(account, settingsId, now);
    }

    private void seedRisk(PreparationFixture fixture, long riskAge, long dailyPnl, int buyCount) {
        jdbc.sql("""
                INSERT INTO daily_risk_states
                    (user_id, connection_id, trading_date, settings_id, starting_equity_krw,
                     daily_pnl_krw, daily_loss_limit_krw, buy_count, baseline_at, as_of)
                VALUES (:userId, :connectionId, :date, :settingsId, 100000,
                        :dailyPnl, 10000, :buyCount, :baseline, :asOf)
                """)
                .param("userId", memberId(fixture.account()))
                .param("connectionId", fixture.account().getConnectionId())
                .param("date", fixture.now().toLocalDate())
                .param("settingsId", fixture.settingsId())
                .param("dailyPnl", dailyPnl)
                .param("buyCount", buyCount)
                .param("baseline", fixture.now().toLocalDate().atTime(8, 0))
                .param("asOf", fixture.now().minusSeconds(riskAge).toLocalDateTime())
                .update();
    }

    private long countOrders(BrokerConnection account) {
        return jdbc.sql("SELECT count(*) FROM orders WHERE connection_id = :connectionId")
                .param("connectionId", account.getConnectionId())
                .query(Long.class)
                .single();
    }

    private record PreparationFixture(BrokerConnection account, long settingsId, OffsetDateTime now) {}

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
