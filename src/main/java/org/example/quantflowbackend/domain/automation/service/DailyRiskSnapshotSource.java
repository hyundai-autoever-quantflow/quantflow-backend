package org.example.quantflowbackend.domain.automation.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/** Supplies verified, account-wide values in whole KRW for a trading date in Asia/Seoul. */
public interface DailyRiskSnapshotSource {
    Optional<Snapshot> fetch(Long memberId, Long connectionId, LocalDate tradingDate);

    /**
     * Starting equity is measured at 00:00 Seoul time on the requested trading date.
     * Current equity and net external flows (positive for inflows) cover the same account through observedAt.
     */
    record Snapshot(BigDecimal startingEquityKrw, BigDecimal currentEquityKrw,
                    BigDecimal netExternalFlowKrw, Instant observedAt) {}
}
