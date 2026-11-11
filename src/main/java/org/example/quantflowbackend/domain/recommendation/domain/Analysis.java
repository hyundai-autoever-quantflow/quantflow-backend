package org.example.quantflowbackend.domain.recommendation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.quantflowbackend.domain.automation.domain.TradingSettings;
import org.example.quantflowbackend.domain.broker.domain.BrokerConnection;
import org.example.quantflowbackend.domain.stock.domain.Stock;
import org.example.quantflowbackend.domain.user.domain.User;
import org.example.quantflowbackend.global.entity.BaseCreatedTimeEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "analyses",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_analyses_1",
                        columnNames = {"analysis_id", "user_id", "connection_id", "settings_id"}
                ),
                @UniqueConstraint(
                        name = "uk_analyses_2",
                        columnNames = {"analysis_id", "user_id", "settings_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Analysis extends BaseCreatedTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "analysis_id")
    private Long analysisId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "trading_date", nullable = false)
    private LocalDate tradingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisStatus status = AnalysisStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 20)
    private AnalysisDecision decision;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "selected_stock_id", nullable = true)
    private Stock selectedStock;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "cutoff_at", nullable = false)
    private LocalDateTime cutoffAt;

    @Column(name = "input_frozen_at")
    private LocalDateTime inputFrozenAt;

    @Column(name = "model_name", nullable = false, length = 150)
    private String modelName;

    @Column(name = "prompt_version", nullable = false, length = 100)
    private String promptVersion;

    @Column(name = "strategy_version", nullable = false, length = 100)
    private String strategyVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "settings_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> settingsSnapshot;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "connection_id", nullable = true)
    private BrokerConnection connection;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "settings_id", nullable = true)
    private TradingSettings settings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> preferencesSnapshot;

    @Column(name = "prompt_hash", length = 64)
    private String promptHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_snapshot", nullable = true, columnDefinition = "jsonb")
    private Map<String, Object> requestSnapshot;

    @Column(name = "raw_output", columnDefinition = "TEXT")
    private String rawOutput;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_result", nullable = true, columnDefinition = "jsonb")
    private Map<String, Object> validationResult;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Builder
    private Analysis(
            User user,
            LocalDate tradingDate,
            LocalDateTime cutoffAt,
            String modelName,
            String promptVersion,
            String strategyVersion,
            Map<String, Object> settingsSnapshot,
            BrokerConnection connection,
            TradingSettings settings,
            Map<String, Object> preferencesSnapshot
    ) {
        this.user = Objects.requireNonNull(user, "필수 값입니다: user");
        this.tradingDate = Objects.requireNonNull(tradingDate, "필수 값입니다: tradingDate");
        this.cutoffAt = Objects.requireNonNull(cutoffAt, "필수 값입니다: cutoffAt");
        this.modelName = Objects.requireNonNull(modelName, "필수 값입니다: modelName");
        this.promptVersion = Objects.requireNonNull(promptVersion, "필수 값입니다: promptVersion");
        this.strategyVersion = Objects.requireNonNull(strategyVersion, "필수 값입니다: strategyVersion");
        this.settingsSnapshot = new HashMap<>(Objects.requireNonNull(settingsSnapshot, "필수 값입니다: settingsSnapshot"));
        this.connection = connection;
        this.settings = settings;
        this.preferencesSnapshot = new HashMap<>(Objects.requireNonNull(preferencesSnapshot, "필수 값입니다: preferencesSnapshot"));
    }

    public static Analysis create(
            User user,
            LocalDate tradingDate,
            LocalDateTime cutoffAt,
            String modelName,
            String promptVersion,
            String strategyVersion,
            Map<String, Object> settingsSnapshot,
            BrokerConnection connection,
            TradingSettings settings,
            Map<String, Object> preferencesSnapshot
    ) {
        return Analysis.builder()
                .user(user)
                .tradingDate(tradingDate)
                .cutoffAt(cutoffAt)
                .modelName(modelName)
                .promptVersion(promptVersion)
                .strategyVersion(strategyVersion)
                .settingsSnapshot(settingsSnapshot)
                .connection(connection)
                .settings(settings)
                .preferencesSnapshot(preferencesSnapshot)
                .build();
    }
}
