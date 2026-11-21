package org.example.quantflowbackend.domain.automation.repository;

import org.example.quantflowbackend.domain.automation.domain.Automation;
import org.example.quantflowbackend.domain.automation.domain.AutomationDesiredState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AutomationRepository extends JpaRepository<Automation, Long> {
    boolean existsByUserMemberIdAndDesiredStateAndKillSwitchActiveFalse(
            Long memberId,
            AutomationDesiredState desiredState
    );

    Optional<Automation> findByConnectionConnectionIdAndUserMemberId(
            Long connectionId, Long memberId
    );

    @Query("""
            SELECT a.user.memberId AS memberId, a.connection.connectionId AS connectionId
            FROM Automation a
            WHERE a.desiredState = RUNNING AND a.killSwitchActive = false
                AND a.connection.broker = TOSS AND a.connection.status = CONNECTED
                AND a.settings.status = ACTIVE AND a.settings.effectiveTradingDate <= :date
                AND NOT EXISTS (
                    SELECT 1 FROM Order o
                    WHERE o.connection.connectionId = a.connection.connectionId
                        AND o.recommendationTradingDate = :date AND o.side = BUY AND o.triggerType = ENTRY_RULE
                        AND (o.status <> CREATED OR o.expiresAt <= :now)
                )
            ORDER BY a.connection.connectionId
            """)
    List<ScheduledBuyAccount> findScheduledBuyAccounts(@Param("date") LocalDate date,
                                                       @Param("now") LocalDateTime now);

    interface ScheduledBuyAccount {
        Long getMemberId();

        Long getConnectionId();
    }
}
