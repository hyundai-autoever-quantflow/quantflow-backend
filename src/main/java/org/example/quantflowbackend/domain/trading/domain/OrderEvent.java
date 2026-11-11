package org.example.quantflowbackend.domain.trading.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "order_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_order_events_1",
                        columnNames = {"order_id", "source_event_key"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_event_id")
    private Long orderEventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "source_event_key", nullable = false, length = 200)
    private String sourceEventKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private OrderEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    private OrderStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private OrderStatus newStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sanitized_payload", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> sanitizedPayload;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Builder
    private OrderEvent(
            Order order,
            String sourceEventKey,
            OrderEventType eventType,
            OrderStatus previousStatus,
            OrderStatus newStatus,
            Map<String, Object> sanitizedPayload,
            LocalDateTime occurredAt,
            LocalDateTime receivedAt
    ) {
        this.order = Objects.requireNonNull(order, "필수 값입니다: order");
        this.sourceEventKey = Objects.requireNonNull(sourceEventKey, "필수 값입니다: sourceEventKey");
        this.eventType = Objects.requireNonNull(eventType, "필수 값입니다: eventType");
        this.previousStatus = previousStatus;
        this.newStatus = Objects.requireNonNull(newStatus, "필수 값입니다: newStatus");
        this.sanitizedPayload = new HashMap<>(Objects.requireNonNull(sanitizedPayload, "필수 값입니다: sanitizedPayload"));
        this.occurredAt = Objects.requireNonNull(occurredAt, "필수 값입니다: occurredAt");
        this.receivedAt = Objects.requireNonNull(receivedAt, "필수 값입니다: receivedAt");
    }

    public static OrderEvent create(
            Order order,
            String sourceEventKey,
            OrderEventType eventType,
            OrderStatus previousStatus,
            OrderStatus newStatus,
            Map<String, Object> sanitizedPayload,
            LocalDateTime occurredAt,
            LocalDateTime receivedAt
    ) {
        return OrderEvent.builder()
                .order(order)
                .sourceEventKey(sourceEventKey)
                .eventType(eventType)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .sanitizedPayload(sanitizedPayload)
                .occurredAt(occurredAt)
                .receivedAt(receivedAt)
                .build();
    }
}
