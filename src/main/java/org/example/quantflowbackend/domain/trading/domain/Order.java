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
import org.example.quantflowbackend.global.entity.BaseTimeEntity;
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
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_orders_1",
                        columnNames = {"client_order_key"}
                ),
                @UniqueConstraint(
                        name = "uk_orders_2",
                        columnNames = {"connection_id", "broker_order_date", "broker_order_no"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_id")
    private Analysis analysis;

    @Column(name = "recommendation_trading_date")
    private LocalDate recommendationTradingDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "position_id", nullable = true)
    private Position position;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_id", nullable = false)
    private Stock stock;

    @Column(name = "client_order_key", nullable = false, length = 150)
    private String clientOrderKey;

    @Column(name = "broker_order_no", length = 100)
    private String brokerOrderNo;

    @Column(name = "broker_order_date")
    private LocalDate brokerOrderDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 4)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 10)
    private OrderType orderType;

    @Column(name = "quantity", nullable = false, precision = 20, scale = 0)
    private BigDecimal quantity;

    @Column(name = "limit_price", precision = 20, scale = 4)
    private BigDecimal limitPrice;

    @Column(name = "filled_quantity", nullable = false, precision = 20, scale = 0)
    private BigDecimal filledQuantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status = OrderStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 30)
    private OrderTriggerType triggerType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "decision_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> decisionSnapshot;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "connection_id", nullable = false)
    private BrokerConnection connection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settings_id", nullable = false)
    private TradingSettings settings;

    @Column(name = "exchange_route", nullable = false, length = 10)
    private String exchangeRoute = "KRX";

    @Column(name = "reference_price", nullable = false, precision = 20, scale = 4)
    private BigDecimal referencePrice;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "broker_org_no", length = 100)
    private String brokerOrgNo;

    @Builder
    private Order(
            Analysis analysis,
            LocalDate recommendationTradingDate,
            Position position,
            Stock stock,
            String clientOrderKey,
            OrderSide side,
            OrderType orderType,
            BigDecimal quantity,
            BigDecimal limitPrice,
            OrderTriggerType triggerType,
            Map<String, Object> decisionSnapshot,
            User user,
            BrokerConnection connection,
            TradingSettings settings,
            BigDecimal referencePrice,
            LocalDateTime expiresAt
    ) {
        if ((analysis == null) == (recommendationTradingDate == null)) {
            throw new IllegalArgumentException("주문 출처는 하나만 지정해야 합니다.");
        }
        this.analysis = analysis;
        this.recommendationTradingDate = recommendationTradingDate;
        this.position = position;
        this.stock = Objects.requireNonNull(stock, "필수 값입니다: stock");
        this.clientOrderKey = Objects.requireNonNull(clientOrderKey, "필수 값입니다: clientOrderKey");
        this.side = Objects.requireNonNull(side, "필수 값입니다: side");
        this.orderType = Objects.requireNonNull(orderType, "필수 값입니다: orderType");
        this.quantity = Objects.requireNonNull(quantity, "필수 값입니다: quantity");
        this.limitPrice = limitPrice;
        this.triggerType = Objects.requireNonNull(triggerType, "필수 값입니다: triggerType");
        this.decisionSnapshot = new HashMap<>(Objects.requireNonNull(decisionSnapshot, "필수 값입니다: decisionSnapshot"));
        this.user = Objects.requireNonNull(user, "필수 값입니다: user");
        this.connection = Objects.requireNonNull(connection, "필수 값입니다: connection");
        this.settings = Objects.requireNonNull(settings, "필수 값입니다: settings");
        this.referencePrice = Objects.requireNonNull(referencePrice, "필수 값입니다: referencePrice");
        this.expiresAt = Objects.requireNonNull(expiresAt, "필수 값입니다: expiresAt");
    }

    public static Order create(
            Analysis analysis,
            Position position,
            Stock stock,
            String clientOrderKey,
            OrderSide side,
            OrderType orderType,
            BigDecimal quantity,
            BigDecimal limitPrice,
            OrderTriggerType triggerType,
            Map<String, Object> decisionSnapshot,
            User user,
            BrokerConnection connection,
            TradingSettings settings,
            BigDecimal referencePrice,
            LocalDateTime expiresAt
    ) {
        return Order.builder()
                .analysis(analysis)
                .position(position)
                .stock(stock)
                .clientOrderKey(clientOrderKey)
                .side(side)
                .orderType(orderType)
                .quantity(quantity)
                .limitPrice(limitPrice)
                .triggerType(triggerType)
                .decisionSnapshot(decisionSnapshot)
                .user(user)
                .connection(connection)
                .settings(settings)
                .referencePrice(referencePrice)
                .expiresAt(expiresAt)
                .build();
    }

    public static Order createAutoBuy(
            LocalDate recommendationTradingDate,
            Stock stock,
            String clientOrderKey,
            BigDecimal quantity,
            BigDecimal limitPrice,
            Map<String, Object> decisionSnapshot,
            User user,
            BrokerConnection connection,
            TradingSettings settings,
            BigDecimal referencePrice,
            LocalDateTime expiresAt
    ) {
        return Order.builder()
                .recommendationTradingDate(
                        Objects.requireNonNull(recommendationTradingDate)
                )
                .stock(stock)
                .clientOrderKey(clientOrderKey)
                .side(OrderSide.BUY)
                .orderType(OrderType.LIMIT)
                .quantity(quantity)
                .limitPrice(Objects.requireNonNull(limitPrice))
                .triggerType(OrderTriggerType.ENTRY_RULE)
                .decisionSnapshot(decisionSnapshot)
                .user(user)
                .connection(connection)
                .settings(settings)
                .referencePrice(referencePrice)
                .expiresAt(expiresAt)
                .build();
    }
}
