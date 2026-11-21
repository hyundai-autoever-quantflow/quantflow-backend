package org.example.quantflowbackend.domain.automation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.quantflowbackend.domain.broker.domain.BrokerConnection;
import org.example.quantflowbackend.domain.user.domain.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "daily_risk_states",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_daily_risk_states_1",
                        columnNames = {"connection_id", "trading_date"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyRiskState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "risk_state_id")
    private Long riskStateId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private BrokerConnection connection;

    @Column(name = "trading_date", nullable = false)
    private LocalDate tradingDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settings_id", nullable = false)
    private TradingSettings settings;

    @Column(name = "starting_equity_krw", nullable = false, precision = 20, scale = 0)
    private BigDecimal startingEquityKrw;

    @Column(name = "current_equity_krw", precision = 20, scale = 0)
    private BigDecimal currentEquityKrw;

    @Column(name = "net_cash_flow_krw", precision = 20, scale = 0)
    private BigDecimal netCashFlowKrw;

    @Column(name = "daily_pnl_krw", precision = 20, scale = 0)
    private BigDecimal dailyPnlKrw;

    @Column(name = "daily_loss_limit_krw", nullable = false, precision = 20, scale = 0)
    private BigDecimal dailyLossLimitKrw;

    @Column(name = "consecutive_losses", nullable = false)
    private Integer consecutiveLosses = 0;

    @Column(name = "buy_count", nullable = false)
    private Integer buyCount = 0;

    @Column(name = "hard_stop_active", nullable = false)
    private boolean hardStopActive = false;

    @Column(name = "hard_stop_reason", length = 100)
    private String hardStopReason;

    @Column(name = "baseline_at", nullable = false)
    private LocalDateTime baselineAt;

    @Column(name = "as_of", nullable = false)
    private LocalDateTime asOf;

    @Version
    @Column(name = "resource_version", nullable = false)
    private Long resourceVersion;

    @Builder
    private DailyRiskState(
            User user,
            BrokerConnection connection,
            LocalDate tradingDate,
            TradingSettings settings,
            BigDecimal startingEquityKrw,
            BigDecimal dailyLossLimitKrw,
            LocalDateTime baselineAt,
            LocalDateTime asOf
    ) {
        this.user = Objects.requireNonNull(user, "필수 값입니다: user");
        this.connection = Objects.requireNonNull(connection, "필수 값입니다: connection");
        this.tradingDate = Objects.requireNonNull(tradingDate, "필수 값입니다: tradingDate");
        this.settings = Objects.requireNonNull(settings, "필수 값입니다: settings");
        this.startingEquityKrw = Objects.requireNonNull(startingEquityKrw, "필수 값입니다: startingEquityKrw");
        this.dailyLossLimitKrw = Objects.requireNonNull(dailyLossLimitKrw, "필수 값입니다: dailyLossLimitKrw");
        this.baselineAt = Objects.requireNonNull(baselineAt, "필수 값입니다: baselineAt");
        this.asOf = Objects.requireNonNull(asOf, "필수 값입니다: asOf");
    }

    public static DailyRiskState create(
            User user,
            BrokerConnection connection,
            LocalDate tradingDate,
            TradingSettings settings,
            BigDecimal startingEquityKrw,
            BigDecimal dailyLossLimitKrw,
            LocalDateTime baselineAt,
            LocalDateTime asOf
    ) {
        return DailyRiskState.builder()
                .user(user)
                .connection(connection)
                .tradingDate(tradingDate)
                .settings(settings)
                .startingEquityKrw(startingEquityKrw)
                .dailyLossLimitKrw(dailyLossLimitKrw)
                .baselineAt(baselineAt)
                .asOf(asOf)
                .build();
    }

    public void applyEquitySnapshot(BigDecimal startingEquity, BigDecimal currentEquity, BigDecimal netExternalFlow,
                                    BigDecimal dailyPnl, LocalDateTime observedAt) {
        if (dailyPnlKrw != null && !observedAt.isAfter(asOf)) {
            return;
        }
        startingEquityKrw = startingEquity;
        currentEquityKrw = currentEquity;
        netCashFlowKrw = netExternalFlow;
        dailyPnlKrw = dailyPnl;
        baselineAt = tradingDate.atStartOfDay();
        asOf = observedAt;
    }

    public void applySettingsAndBuyCount(TradingSettings settings, int submittedBuys) {
        this.settings = settings;
        dailyLossLimitKrw = settings.getDailyLossLimitKrw();
        buyCount = submittedBuys;
    }

    public void recordBuyAttempt() {
        buyCount++;
    }
}
