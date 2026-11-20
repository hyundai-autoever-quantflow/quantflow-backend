package org.example.quantflowbackend.domain.automation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.quantflowbackend.domain.user.domain.User;
import org.example.quantflowbackend.global.entity.BaseCreatedTimeEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "trading_settings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_trading_settings_1",
                        columnNames = {"user_id", "version"}
                ),
                @UniqueConstraint(
                        name = "uk_trading_settings_2",
                        columnNames = {"settings_id", "user_id"}
                ),
                @UniqueConstraint(
                        name = "uk_trading_settings_3",
                        columnNames = {"user_id", "request_key"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradingSettings extends BaseCreatedTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settings_id")
    private Long settingsId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TradingSettingsStatus status = TradingSettingsStatus.SCHEDULED;

    @Column(name = "effective_trading_date", nullable = false)
    private LocalDate effectiveTradingDate;

    @Column(name = "entry_budget_krw", nullable = false, precision = 20, scale = 0)
    private BigDecimal entryBudgetKrw;

    @Column(name = "max_buys_per_day", nullable = false)
    private Short maxBuysPerDay;

    @Column(name = "take_profit_pct", precision = 9, scale = 4)
    private BigDecimal takeProfitPct;

    @Column(name = "stop_loss_pct", precision = 9, scale = 4)
    private BigDecimal stopLossPct;

    @Column(name = "max_holding_minutes")
    private Integer maxHoldingMinutes;

    @Column(name = "daily_loss_limit_krw", nullable = false, precision = 20, scale = 0)
    private BigDecimal dailyLossLimitKrw;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_limits", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> riskLimits;

    @Column(name = "strategy_version", nullable = false, length = 100)
    private String strategyVersion;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "request_key", nullable = false, length = 150)
    private String requestKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Builder
    private TradingSettings(
            User user,
            Integer version,
            LocalDate effectiveTradingDate,
            BigDecimal entryBudgetKrw,
            Short maxBuysPerDay,
            BigDecimal takeProfitPct,
            BigDecimal stopLossPct,
            Integer maxHoldingMinutes,
            BigDecimal dailyLossLimitKrw,
            Map<String, Object> riskLimits,
            String strategyVersion,
            String requestKey,
            String requestHash
    ) {
        this.user = Objects.requireNonNull(user, "필수 값입니다: user");
        this.version = Objects.requireNonNull(version, "필수 값입니다: version");
        this.effectiveTradingDate = Objects.requireNonNull(effectiveTradingDate, "필수 값입니다: effectiveTradingDate");
        this.entryBudgetKrw = Objects.requireNonNull(entryBudgetKrw, "필수 값입니다: entryBudgetKrw");
        this.maxBuysPerDay = Objects.requireNonNull(maxBuysPerDay, "필수 값입니다: maxBuysPerDay");
        this.takeProfitPct = takeProfitPct;
        this.stopLossPct = stopLossPct;
        this.maxHoldingMinutes = maxHoldingMinutes;
        this.dailyLossLimitKrw = Objects.requireNonNull(dailyLossLimitKrw, "필수 값입니다: dailyLossLimitKrw");
        this.riskLimits = new HashMap<>(Objects.requireNonNull(riskLimits, "필수 값입니다: riskLimits"));
        this.strategyVersion = Objects.requireNonNull(strategyVersion, "필수 값입니다: strategyVersion");
        this.requestKey = Objects.requireNonNull(requestKey, "필수 값입니다: requestKey");
        this.requestHash = Objects.requireNonNull(requestHash, "필수 값입니다: requestHash");
    }

    public static TradingSettings create(
            User user,
            Integer version,
            LocalDate effectiveTradingDate,
            BigDecimal entryBudgetKrw,
            Short maxBuysPerDay,
            BigDecimal takeProfitPct,
            BigDecimal stopLossPct,
            Integer maxHoldingMinutes,
            BigDecimal dailyLossLimitKrw,
            Map<String, Object> riskLimits,
            String strategyVersion,
            String requestKey,
            String requestHash
    ) {
        return TradingSettings.builder()
                .user(user)
                .version(version)
                .effectiveTradingDate(effectiveTradingDate)
                .entryBudgetKrw(entryBudgetKrw)
                .maxBuysPerDay(maxBuysPerDay)
                .takeProfitPct(takeProfitPct)
                .stopLossPct(stopLossPct)
                .maxHoldingMinutes(maxHoldingMinutes)
                .dailyLossLimitKrw(dailyLossLimitKrw)
                .riskLimits(riskLimits)
                .strategyVersion(strategyVersion)
                .requestKey(requestKey)
                .requestHash(requestHash)
                .build();
    }

    public static TradingSettings createForAutoBuy(
            User user,
            Integer version,
            LocalDate effectiveTradingDate,
            BigDecimal entryBudgetKrw,
            Short maxBuysPerDay,
            BigDecimal dailyLossLimitKrw,
            String requestKey,
            String requestHash
    ) {
        return TradingSettings.create(
                user,
                version,
                effectiveTradingDate,
                entryBudgetKrw,
                maxBuysPerDay,
                null,
                null,
                null,
                dailyLossLimitKrw,
                Map.of(),
                "auto-buy-v1",
                requestKey,
                requestHash
        );
    }
}
