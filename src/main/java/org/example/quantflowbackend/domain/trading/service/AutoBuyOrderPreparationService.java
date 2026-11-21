package org.example.quantflowbackend.domain.trading.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.automation.domain.AutomationDesiredState;
import org.example.quantflowbackend.domain.automation.domain.TradingSettingsStatus;
import org.example.quantflowbackend.domain.automation.repository.AutomationRepository;
import org.example.quantflowbackend.domain.automation.service.DailyRiskStateService;
import org.example.quantflowbackend.domain.broker.client.TossOrderClient;
import org.example.quantflowbackend.domain.broker.domain.BrokerConnectionStatus;
import org.example.quantflowbackend.domain.broker.domain.BrokerType;
import org.example.quantflowbackend.domain.broker.dto.TossMarketCalendarResponse.RegularSession;
import org.example.quantflowbackend.domain.broker.service.TossAccountQueryService;
import org.example.quantflowbackend.domain.recommendation.service.RankingStockCandidateService;
import org.example.quantflowbackend.domain.trading.domain.Order;
import org.example.quantflowbackend.domain.trading.domain.OrderSide;
import org.example.quantflowbackend.domain.trading.domain.OrderTriggerType;
import org.example.quantflowbackend.domain.trading.repository.OrderRepository;
import org.example.quantflowbackend.domain.trading.service.BuyOrderContextService.BuyOrderContext;
import org.example.quantflowbackend.domain.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AutoBuyOrderPreparationService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Duration MAX_QUOTE_AGE = Duration.ofSeconds(30);

    private final BuyOrderContextService contexts;
    private final RankingStockCandidateService market;
    private final UserRepository users;
    private final AutomationRepository automations;
    private final DailyRiskStateService risks;
    private final OrderRepository orders;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;
    private final TossAccountQueryService accountQueries;
    private final TossOrderClient tossOrders;

    public Long prepare(Long memberId, Long connectionId) {
        var context = contexts.load(memberId, connectionId);
        var session = regularSession(context.tradingDate());
        risks.refreshIfAvailable(memberId, connectionId, context.tradingDate());
        var stock = context.stock();
        BigDecimal commissionRate = accountQueries.read(memberId, connectionId,
                (token, accountSeq) -> tossOrders.domesticBuyCommission(
                        token, accountSeq, stock.getMarket(), stock.getSymbol(), context.tradingDate()));

        return new TransactionTemplate(transactionManager).execute(
                transaction -> savePreparedOrder(memberId, connectionId, context, session, commissionRate)
        );
    }

    private RegularSession regularSession(LocalDate date) {
        var day = market.marketCalendar(date).today();
        if (!date.equals(day.date())) {
            throw marketUnavailable();
        }
        if (day.integrated() == null || day.integrated().regularMarket() == null) {
            throw conflict("정규장이 운영되지 않는 거래일입니다.");
        }

        var session = day.integrated().regularMarket();
        if (session.startTime() == null || session.endTime() == null
                || !session.startTime().isBefore(session.endTime())
                || !date.equals(session.startTime().atZoneSameInstant(SEOUL).toLocalDate())
                || !date.equals(session.endTime().atZoneSameInstant(SEOUL).toLocalDate())) {
            throw marketUnavailable();
        }
        return session;
    }

    private Long savePreparedOrder(
            Long memberId, Long connectionId, BuyOrderContext context, RegularSession session, BigDecimal commissionRate
    ) {
        // Serialize the reservation with automation enable and stop for the same member.
        var user = users.findByMemberId(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "회원 인증이 필요합니다."));
        var automation = automations.findByConnectionConnectionIdAndUserMemberId(connectionId, memberId)
                .orElseThrow(() -> conflict("이 계좌의 자동매수를 켜주세요."));
        var connection = automation.getConnection();
        var settings = automation.getSettings();
        LocalDate date = context.tradingDate();

        if (connection.getBroker() != BrokerType.TOSS || connection.getStatus() != BrokerConnectionStatus.CONNECTED
                || automation.getDesiredState() != AutomationDesiredState.RUNNING || automation.isKillSwitchActive()
                || !settings.getSettingsId().equals(context.settings().settingsId())
                || settings.getStatus() != TradingSettingsStatus.ACTIVE
                || settings.getEffectiveTradingDate().isAfter(date)) {
            throw conflict("계좌 상태 또는 자동매수 설정이 변경되었습니다.");
        }
        var checkedRisk = risks.lockAndValidate(connectionId, date, settings);
        var risk = checkedRisk.state();
        if (orders.existsByConnectionConnectionIdAndRecommendationTradingDateAndSideAndTriggerType(
                connectionId, date, OrderSide.BUY, OrderTriggerType.ENTRY_RULE)) {
            throw conflict("이 계좌의 오늘 자동매수 주문이 이미 존재합니다.");
        }

        var now = clock.instant();
        if (now.isBefore(session.startTime().toInstant()) || !now.isBefore(session.endTime().toInstant())) {
            throw conflict("정규장 거래 시간에만 자동매수 주문을 준비할 수 있습니다.");
        }

        var quoteTime = context.quote().timestamp();
        if (quoteTime == null || quoteTime.isBefore(session.startTime().toInstant())
                || quoteTime.isAfter(now) || !now.isBefore(quoteTime.plus(MAX_QUOTE_AGE))) {
            throw conflict("정규장의 최신 시세를 확인할 수 없습니다.");
        }

        BigDecimal limitPrice = context.quote().lastPrice().stripTrailingZeros();
        if (limitPrice.scale() > 0) {
            throw conflict("국내주식 지정가를 원 단위로 확인할 수 없습니다.");
        }
        BigDecimal budget = context.usableBudgetKrw().setScale(0, RoundingMode.DOWN);
        BigDecimal unitCost = limitPrice.multiply(BigDecimal.ONE.add(commissionRate));
        BigDecimal quantity = budget.divide(unitCost, 0, RoundingMode.DOWN);
        if (quantity.signum() == 0) {
            throw conflict("매수 가능 금액으로 한 주를 주문할 수 없습니다.");
        }

        LocalDateTime expiresAt = Collections.min(List.of(
                LocalDateTime.ofInstant(session.endTime().toInstant(), SEOUL),
                LocalDateTime.ofInstant(quoteTime.plus(MAX_QUOTE_AGE), SEOUL),
                checkedRisk.validUntil()
        ));
        var decision = Map.<String, Object>of(
                "cashBuyingPowerKrw", context.availableCashKrw(),
                "usableBudgetKrw", budget,
                "grossOrderAmountKrw", limitPrice.multiply(quantity),
                "estimatedCommissionKrw", limitPrice.multiply(quantity).multiply(commissionRate)
                        .setScale(0, RoundingMode.CEILING),
                "quoteTime", quoteTime.toString(),
                "dailyPnlKrw", risk.getDailyPnlKrw(),
                "buyCount", risk.getBuyCount(),
                "riskAsOf", risk.getAsOf().toString()
        );
        String clientOrderKey = "ab-" + date + "-" + connectionId;
        var order = Order.createAutoBuy(date, context.stock(), clientOrderKey, quantity, limitPrice,
                decision, user, connection, settings, limitPrice, expiresAt);
        return orders.saveAndFlush(order).getOrderId();
    }

    private ResponseStatusException marketUnavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "정규장 운영 시간을 확인할 수 없습니다.");
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
