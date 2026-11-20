package org.example.quantflowbackend.domain.recommendation.scheduler;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.recommendation.service.DailyRecommendationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.daily-recommendation",
        name = "schedule-enabled", havingValue = "true")
public class DailyRecommendationScheduler {
    private final DailyRecommendationService service;

    @Value("${app.daily-recommendation.query:증시}")
    private String query;

    @Scheduled(cron = "${app.daily-recommendation.cron:0 30 7 * * MON-FRI}",
            zone = "Asia/Seoul")
    public void generate() {
        service.generateToday(query);
    }
}
