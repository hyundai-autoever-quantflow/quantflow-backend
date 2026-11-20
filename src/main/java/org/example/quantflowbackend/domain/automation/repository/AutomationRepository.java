package org.example.quantflowbackend.domain.automation.repository;

import org.example.quantflowbackend.domain.automation.domain.Automation;
import org.example.quantflowbackend.domain.automation.domain.AutomationDesiredState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AutomationRepository extends JpaRepository<Automation, Long> {
    boolean existsByUserMemberIdAndDesiredStateAndKillSwitchActiveFalse(
            Long memberId,
            AutomationDesiredState desiredState
    );

    Optional<Automation> findByConnectionConnectionIdAndUserMemberId(
            Long connectionId, Long memberId
    );
}
