package org.example.quantflowbackend.domain.automation.scheduler;

import org.example.quantflowbackend.domain.automation.repository.AutomationRepository;
import org.example.quantflowbackend.domain.recommendation.service.DailyRecommendationService;
import org.example.quantflowbackend.domain.trading.service.AutoBuyOrderExecutionService;
import org.example.quantflowbackend.domain.trading.service.DomesticMarketSessionService;
import org.example.quantflowbackend.global.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AutoBuySchedulerConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SchedulingConfig.class, AutoBuyScheduler.class)
            .withBean(DailyRecommendationService.class, () -> mock(DailyRecommendationService.class))
            .withBean(DomesticMarketSessionService.class, () -> mock(DomesticMarketSessionService.class))
            .withBean(AutomationRepository.class, () -> mock(AutomationRepository.class))
            .withBean(AutoBuyOrderExecutionService.class, () -> mock(AutoBuyOrderExecutionService.class))
            .withBean(Clock.class, Clock::systemUTC);

    @Test
    void hasNoScheduledOrderTaskByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(AutoBuyScheduler.class);
            assertThat(context.getBean(ScheduledTaskHolder.class).getScheduledTasks()).isEmpty();
        });
    }

    @Test
    void registersTheOrderTaskWhenEnabled() {
        contextRunner.withPropertyValues("app.auto-buy.schedule-enabled=true", "app.auto-buy.cron=0 0 0 1 1 *")
                .run(context -> {
                    assertThat(context).hasSingleBean(AutoBuyScheduler.class);
                    assertThat(context.getBean(ScheduledTaskHolder.class).getScheduledTasks()).hasSize(1);
                });
    }
}
