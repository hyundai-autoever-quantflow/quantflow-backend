package org.example.quantflowbackend.domain.automation.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.quantflowbackend.domain.broker.domain.BrokerConnection;
import org.example.quantflowbackend.domain.user.domain.User;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "automations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_automations_1",
                        columnNames = {"connection_id"}
                ),
                @UniqueConstraint(
                        name = "uk_automations_2",
                        columnNames = {"automation_id", "user_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Automation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "automation_id")
    private Long automationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private BrokerConnection connection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settings_id", nullable = false)
    private TradingSettings settings;

    @Enumerated(EnumType.STRING)
    @Column(name = "desired_state", nullable = false, length = 20)
    private AutomationDesiredState desiredState = AutomationDesiredState.STOPPED;

    @Enumerated(EnumType.STRING)
    @Column(name = "runtime_state", nullable = false, length = 20)
    private AutomationRuntimeState runtimeState = AutomationRuntimeState.IDLE;

    @Column(name = "kill_switch_active", nullable = false)
    private boolean killSwitchActive = false;

    @Column(name = "block_reason", length = 100)
    private String blockReason;

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "control_receipts", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> controlReceipts = new ArrayList<>();

    @Version
    @Column(name = "resource_version", nullable = false)
    private Long resourceVersion;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Automation(
            User user,
            BrokerConnection connection,
            TradingSettings settings
    ) {
        this.user = Objects.requireNonNull(user, "필수 값입니다: user");
        this.connection = Objects.requireNonNull(connection, "필수 값입니다: connection");
        this.settings = Objects.requireNonNull(settings, "필수 값입니다: settings");
    }

    public static Automation create(
            User user,
            BrokerConnection connection,
            TradingSettings settings
    ) {
        return Automation.builder()
                .user(user)
                .connection(connection)
                .settings(settings)
                .build();
    }
}
