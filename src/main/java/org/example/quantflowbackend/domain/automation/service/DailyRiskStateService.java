package org.example.quantflowbackend.domain.automation.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.automation.domain.DailyRiskState;
import org.example.quantflowbackend.domain.automation.domain.TradingSettings;
import org.example.quantflowbackend.domain.automation.repository.AutomationRepository;
import org.example.quantflowbackend.domain.automation.repository.DailyRiskStateRepository;
import org.example.quantflowbackend.domain.automation.service.DailyRiskSnapshotSource.Snapshot;
import org.example.quantflowbackend.domain.trading.domain.OrderEventType;
import org.example.quantflowbackend.domain.trading.domain.OrderSide;
import org.example.quantflowbackend.domain.trading.domain.OrderTriggerType;
import org.example.quantflowbackend.domain.trading.repository.OrderEventRepository;
import org.example.quantflowbackend.domain.user.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class DailyRiskStateService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Duration MAX_RISK_AGE = Duration.ofMinutes(1);

    private final DailyRiskStateRepository states;
    private final OrderEventRepository events;
    private final UserRepository users;
    private final AutomationRepository automations;
    private final ObjectProvider<DailyRiskSnapshotSource> sources;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    public void refreshIfAvailable(Long memberId, Long connectionId, LocalDate tradingDate) {
        var source = sources.getIfAvailable();
        if (source == null) {
            return;
        }
        source.fetch(memberId, connectionId, tradingDate).ifPresent(snapshot ->
                new TransactionTemplate(transactionManager).executeWithoutResult(
                        status -> saveSnapshot(memberId, connectionId, tradingDate, snapshot)
                ));
    }

    private void saveSnapshot(Long memberId, Long connectionId, LocalDate date, Snapshot snapshot) {
        var observed = snapshot.observedAt();
        if (observed == null || observed.isAfter(clock.instant())) {
            throw invalidSnapshot();
        }
        var asOf = LocalDateTime.ofInstant(observed.truncatedTo(ChronoUnit.MICROS), SEOUL);
        if (!date.equals(asOf.toLocalDate()) || !validKrw(snapshot.startingEquityKrw())
                || !validKrw(snapshot.currentEquityKrw()) || !validKrw(snapshot.netExternalFlowKrw())
                || snapshot.startingEquityKrw().signum() < 0 || snapshot.currentEquityKrw().signum() < 0) {
            throw invalidSnapshot();
        }
        var pnl = snapshot.currentEquityKrw().subtract(snapshot.startingEquityKrw())
                .subtract(snapshot.netExternalFlowKrw());
        if (!validKrw(pnl)) {
            throw invalidSnapshot();
        }

        var user = users.findByMemberId(memberId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "회원 인증이 필요합니다."));
        var automation = automations.findByConnectionConnectionIdAndUserMemberId(connectionId, memberId)
                .orElseThrow(() -> conflict("계좌의 자동매수 설정을 찾을 수 없습니다."));
        var settings = automation.getSettings();
        var state = states.findByConnectionConnectionIdAndTradingDate(connectionId, date)
                .orElseGet(() -> DailyRiskState.create(user, automation.getConnection(), date, settings,
                        snapshot.startingEquityKrw(), settings.getDailyLossLimitKrw(), date.atStartOfDay(), asOf));
        state.applyEquitySnapshot(snapshot.startingEquityKrw(), snapshot.currentEquityKrw(),
                snapshot.netExternalFlowKrw(), pnl, asOf);
        state.applySettingsAndBuyCount(settings, countSubmittedBuys(connectionId, date));
        states.save(state);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public CheckedRisk lockAndValidate(Long connectionId, LocalDate date, TradingSettings settings) {
        var risk = states.findByConnectionConnectionIdAndTradingDate(connectionId, date)
                .orElseThrow(() -> conflict("오늘의 계좌 손익을 확인할 수 없습니다."));
        var now = LocalDateTime.ofInstant(clock.instant(), SEOUL);
        var validUntil = risk.getAsOf().plus(MAX_RISK_AGE);
        if (risk.getDailyPnlKrw() == null || !date.equals(risk.getAsOf().toLocalDate())
                || risk.getAsOf().isAfter(now) || !now.isBefore(validUntil)) {
            throw conflict("최신 계좌 손익을 확인할 수 없습니다.");
        }
        if (risk.isHardStopActive() || risk.getDailyPnlKrw().compareTo(settings.getDailyLossLimitKrw().negate()) <= 0) {
            throw conflict("일일 손실 한도 또는 계좌 중지 설정으로 주문을 보류합니다.");
        }

        int submitted = countSubmittedBuys(connectionId, date);
        if (submitted >= settings.getMaxBuysPerDay()) {
            throw conflict("일일 최대 매수 횟수에 도달했습니다.");
        }
        risk.applySettingsAndBuyCount(settings, submitted);
        return new CheckedRisk(risk, validUntil);
    }

    private int countSubmittedBuys(Long connectionId, LocalDate date) {
        return Math.toIntExact(events.countDailyOrderAttempts(
                connectionId, date, OrderSide.BUY, OrderTriggerType.ENTRY_RULE, OrderEventType.SUBMIT));
    }

    private boolean validKrw(BigDecimal value) {
        if (value == null) {
            return false;
        }
        var normalized = value.stripTrailingZeros();
        return normalized.scale() <= 0 && (long) normalized.precision() - normalized.scale() <= 20;
    }

    private ResponseStatusException invalidSnapshot() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "계좌 전체 일일 손익 데이터의 기준을 확인할 수 없습니다.");
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record CheckedRisk(DailyRiskState state, LocalDateTime validUntil) {}
}
