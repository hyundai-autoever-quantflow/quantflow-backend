package org.example.quantflowbackend.domain.recommendation.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.quantflowbackend.domain.recommendation.service.RankingStockCandidateService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.ranking-snapshot",
        name = "schedule-enabled",
        havingValue = "true"
)
public class RankingSnapshotScheduler {
    private final RankingStockCandidateService candidateService;

    @Scheduled(
            cron = "${app.ranking-snapshot.cron:0 10 21 * * MON-FRI}",
            zone = "Asia/Seoul"
    )
    public void capture() {
        try {
            var snapshot = candidateService.captureToday();

            log.info("랭킹 저장 완료: rankingDate={}, collectedAt={}",
                    snapshot.rankingDate(), snapshot.collectedAt());
        } catch (ResponseStatusException exception) {
            log.warn("랭킹 저장 실패: status={}, reason={}",
                    exception.getStatusCode(), exception.getReason());
        }
    }
}
