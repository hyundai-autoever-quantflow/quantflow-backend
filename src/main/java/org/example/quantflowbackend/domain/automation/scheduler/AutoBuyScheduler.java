package org.example.quantflowbackend.domain.automation.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.quantflowbackend.domain.automation.repository.AutomationRepository;
import org.example.quantflowbackend.domain.recommendation.service.DailyRecommendationService;
import org.example.quantflowbackend.domain.trading.service.AutoBuyOrderExecutionService;
import org.example.quantflowbackend.domain.trading.service.DomesticMarketSessionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.auto-buy", name = "schedule-enabled", havingValue = "true")
public class AutoBuyScheduler {
    private static final String SEOUL = "Asia/Seoul";

    private final DailyRecommendationService recommendations;
    private final DomesticMarketSessionService market;
    private final AutomationRepository automations;
    private final AutoBuyOrderExecutionService orders;
    private final Clock clock;

    @Scheduled(cron = "${app.auto-buy.cron:0 * 8-16 * * *}", zone = SEOUL)
    public void run() {
        var now = LocalDateTime.ofInstant(clock.instant(), ZoneId.of(SEOUL));
        var date = now.toLocalDate();
        var recommendation = recommendations.findToday()
                .filter(result -> date.equals(result.tradingDate()) && result.stock() != null);
        if (recommendation.isEmpty()) {
            return;
        }

        var session = market.findRegularSession(date).orElse(null);
        if (session == null || !session.isOpenAt(clock.instant())) {
            return;
        }
        for (var account : automations.findScheduledBuyAccounts(date, now)) {
            if (!session.isOpenAt(clock.instant())) {
                return;
            }
            try {
                var result = orders.executeToday(account.getMemberId(), account.getConnectionId());
                log.info("예약 자동매수 처리 connectionId={}, orderId={}, status={}",
                        account.getConnectionId(), result.orderId(), result.status());
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode().is5xxServerError()) {
                    log.warn("예약 자동매수 조회 실패 connectionId={}, status={}",
                            account.getConnectionId(), exception.getStatusCode());
                } else {
                    log.debug("예약 자동매수 보류 connectionId={}, status={}",
                            account.getConnectionId(), exception.getStatusCode());
                }
            } catch (RuntimeException exception) {
                log.error("예약 자동매수 처리 오류 connectionId={}, error={}",
                        account.getConnectionId(), exception.getClass().getSimpleName());
            }
        }
    }
}
