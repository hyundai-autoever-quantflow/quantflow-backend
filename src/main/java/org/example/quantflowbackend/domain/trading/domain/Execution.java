package org.example.quantflowbackend.domain.trading.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "executions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_executions_1",
                        columnNames = {"order_id", "execution_key"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Execution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "execution_id")
    private Long executionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "execution_key", nullable = false, length = 200)
    private String executionKey;

    @Column(name = "quantity", nullable = false, precision = 20, scale = 0)
    private BigDecimal quantity;

    @Column(name = "price", nullable = false, precision = 20, scale = 4)
    private BigDecimal price;

    @Column(name = "fee_krw", precision = 20, scale = 0)
    private BigDecimal feeKrw;

    @Column(name = "tax_krw", precision = 20, scale = 0)
    private BigDecimal taxKrw;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_status", nullable = false, length = 20)
    private ExecutionCostStatus costStatus = ExecutionCostStatus.UNKNOWN;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Builder
    private Execution(
            Order order,
            String executionKey,
            BigDecimal quantity,
            BigDecimal price,
            LocalDateTime executedAt,
            LocalDateTime receivedAt
    ) {
        this.order = Objects.requireNonNull(order, "필수 값입니다: order");
        this.executionKey = Objects.requireNonNull(executionKey, "필수 값입니다: executionKey");
        this.quantity = Objects.requireNonNull(quantity, "필수 값입니다: quantity");
        this.price = Objects.requireNonNull(price, "필수 값입니다: price");
        this.executedAt = Objects.requireNonNull(executedAt, "필수 값입니다: executedAt");
        this.receivedAt = Objects.requireNonNull(receivedAt, "필수 값입니다: receivedAt");
    }

    public static Execution create(
            Order order,
            String executionKey,
            BigDecimal quantity,
            BigDecimal price,
            LocalDateTime executedAt,
            LocalDateTime receivedAt
    ) {
        return Execution.builder()
                .order(order)
                .executionKey(executionKey)
                .quantity(quantity)
                .price(price)
                .executedAt(executedAt)
                .receivedAt(receivedAt)
                .build();
    }
}
