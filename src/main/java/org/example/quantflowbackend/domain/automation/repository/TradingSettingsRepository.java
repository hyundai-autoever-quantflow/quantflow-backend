package org.example.quantflowbackend.domain.automation.repository;

import org.example.quantflowbackend.domain.automation.domain.TradingSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TradingSettingsRepository extends JpaRepository<TradingSettings, Long> {
    Optional<TradingSettings> findByUserMemberIdAndRequestKey(
            Long memberId, String requestKey
    );

    Optional<TradingSettings> findFirstByUserMemberIdOrderByVersionDesc(
            Long memberId
    );

    Optional<TradingSettings> findBySettingsIdAndUserMemberId(
            Long settingsId, Long memberId
    );
}
