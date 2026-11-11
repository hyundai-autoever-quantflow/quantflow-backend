package org.example.quantflowbackend.domain.broker.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.quantflowbackend.domain.user.domain.User;
import org.example.quantflowbackend.global.entity.BaseCreatedTimeEntity;
import org.hibernate.annotations.JdbcTypeCode;
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
        name = "broker_connections",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_broker_connections_connection_user",
                        columnNames = {"connection_id", "user_id"}
                ),
                @UniqueConstraint(
                        name = "uk_broker_connections_user_request",
                        columnNames = {"user_id", "request_key"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrokerConnection extends BaseCreatedTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "connection_id")
    private Long connectionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private BrokerType broker;

    @Column(name = "account_fingerprint", nullable = false, updatable = false, length = 128)
    private String accountFingerprint;

    @Column(name = "account_number_masked", nullable = false, updatable = false, length = 50)
    private String accountNumberMasked;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private BrokerEnvironment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BrokerConnectionStatus status = BrokerConnectionStatus.VERIFYING;

    @Column(name = "credential_ref", columnDefinition = "TEXT")
    private String credentialRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> permissions = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false, length = 20)
    private ReconciliationStatus reconciliationStatus = ReconciliationStatus.PENDING;

    @Column(name = "last_reconciled_at")
    private LocalDateTime lastReconciledAt;

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @Column(name = "last_error_code", length = 80)
    private String lastErrorCode;

    @Column(name = "request_key", nullable = false, updatable = false, length = 150)
    private String requestKey;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_receipts", nullable = false, columnDefinition = "jsonb")
    private List<Map<String, Object>> requestReceipts = new ArrayList<>();

    @Version
    @Column(name = "resource_version", nullable = false)
    private Long resourceVersion;

    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;

    @Builder
    private BrokerConnection(
            User user,
            BrokerType broker,
            String accountFingerprint,
            String accountNumberMasked,
            BrokerEnvironment environment,
            String credentialRef,
            String requestKey,
            String requestHash
    ) {
        this.user = Objects.requireNonNull(user, "사용자는 필수입니다.");
        this.broker = Objects.requireNonNull(broker, "증권사는 필수입니다.");
        this.accountFingerprint = Objects.requireNonNull(accountFingerprint, "계좌 식별값은 필수입니다.");
        this.accountNumberMasked = Objects.requireNonNull(accountNumberMasked, "마스킹 계좌번호는 필수입니다.");
        this.environment = Objects.requireNonNull(environment, "투자 환경은 필수입니다.");
        this.credentialRef = credentialRef;
        this.requestKey = Objects.requireNonNull(requestKey, "요청 키는 필수입니다.");
        this.requestHash = Objects.requireNonNull(requestHash, "요청 해시는 필수입니다.");
    }

    public static BrokerConnection create(
            User user,
            BrokerType broker,
            String accountFingerprint,
            String accountNumberMasked,
            BrokerEnvironment environment,
            String credentialRef,
            String requestKey,
            String requestHash
    ) {
        return BrokerConnection.builder()
                .user(user)
                .broker(broker)
                .accountFingerprint(accountFingerprint)
                .accountNumberMasked(accountNumberMasked)
                .environment(environment)
                .credentialRef(credentialRef)
                .requestKey(requestKey)
                .requestHash(requestHash)
                .build();
    }
}
