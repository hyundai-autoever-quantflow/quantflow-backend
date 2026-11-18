package org.example.quantflowbackend.domain.stock.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.quantflowbackend.domain.stock.service.StockSyncService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.stock-sync",
        name = "schedule-enabled",
        havingValue = "true"
)
public class StockSyncScheduler {
    private final StockSyncService stockSyncService;

    @Scheduled(
            cron = "${app.stock-sync.cron:0 0 7 * * *}",
            zone = "Asia/Seoul"
    )
    public void syncStocks() {
        for (String market : List.of("KOSPI", "KOSDAQ")) {
            try {
                var result = stockSyncService.sync(market);
                log.info("종목 동기화 성공: market={}, processedCount={}, checkedAt={}",
                        result.market(),
                        result.processedCount(),
                        result.checkedAt());
            } catch (RuntimeException exception) {
                log.error("종목 동기화 실패: market=" + market, exception);
            }
        }
    }
}
