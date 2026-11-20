package org.example.quantflowbackend.domain.automation.repository;

import jakarta.persistence.LockModeType;
import org.example.quantflowbackend.domain.automation.domain.DailyRiskState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyRiskStateRepository extends JpaRepository<DailyRiskState, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DailyRiskState> findByConnectionConnectionIdAndTradingDate(Long connectionId, LocalDate tradingDate);
}
