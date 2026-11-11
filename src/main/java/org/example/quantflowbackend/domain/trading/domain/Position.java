package org.example.quantflowbackend.domain.trading.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.quantflowbackend.domain.automation.domain.TradingSettings;
import org.example.quantflowbackend.domain.broker.domain.BrokerConnection;
import org.example.quantflowbackend.domain.recommendation.domain.Analysis;
import org.example.quantflowbackend.domain.stock.domain.Stock;
import org.example.quantflowbackend.domain.user.domain.User;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "positions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_positions_1",
                        columnNames = {"position_id", "analysis_id", "stock_id", "connection_id", "user_id", "settings_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Position {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "position_id")
    private Long positionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private Analysis analysis;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "quantity", nullable = false, precision = 20, scale = 0)
    private BigDecimal quantity;

    @Column(name = "average_entry_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal averageEntryPrice;

    @Column(name = "gross_realized_pnl_krw", nullable = false, precision = 20, scale = 0)
    private BigDecimal grossRealizedPnlKrw = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PositionStatus status = PositionStatus.OPEN;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private BrokerConnection connection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settings_id", nullable = false)
    private TradingSettings settings;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Position(
            Analysis analysis,
            Stock stock,
            BigDecimal quantity,
            BigDecimal averageEntryPrice,
            LocalDateTime openedAt,
            User user,
            BrokerConnection connection,
            TradingSettings settings
    ) {
        this.analysis = Objects.requireNonNull(analysis, "필수 값입니다: analysis");
        this.stock = Objects.requireNonNull(stock, "필수 값입니다: stock");
        this.quantity = Objects.requireNonNull(quantity, "필수 값입니다: quantity");
        this.averageEntryPrice = Objects.requireNonNull(averageEntryPrice, "필수 값입니다: averageEntryPrice");
        this.openedAt = Objects.requireNonNull(openedAt, "필수 값입니다: openedAt");
        this.user = Objects.requireNonNull(user, "필수 값입니다: user");
        this.connection = Objects.requireNonNull(connection, "필수 값입니다: connection");
        this.settings = Objects.requireNonNull(settings, "필수 값입니다: settings");
    }

    public static Position create(
            Analysis analysis,
            Stock stock,
            BigDecimal quantity,
            BigDecimal averageEntryPrice,
            LocalDateTime openedAt,
            User user,
            BrokerConnection connection,
            TradingSettings settings
    ) {
        return Position.builder()
                .analysis(analysis)
                .stock(stock)
                .quantity(quantity)
                .averageEntryPrice(averageEntryPrice)
                .openedAt(openedAt)
                .user(user)
                .connection(connection)
                .settings(settings)
                .build();
    }
}
