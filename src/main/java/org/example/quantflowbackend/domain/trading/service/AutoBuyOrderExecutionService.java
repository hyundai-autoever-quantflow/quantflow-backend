package org.example.quantflowbackend.domain.trading.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.automation.domain.AutomationDesiredState;
import org.example.quantflowbackend.domain.automation.domain.TradingSettingsStatus;
import org.example.quantflowbackend.domain.automation.repository.AutomationRepository;
import org.example.quantflowbackend.domain.automation.service.DailyRiskStateService;
import org.example.quantflowbackend.domain.broker.client.TossOrderClient;
import org.example.quantflowbackend.domain.broker.client.TossOrderClient.BuyRequest;
import org.example.quantflowbackend.domain.broker.client.TossOrderClient.OrderDetails;
import org.example.quantflowbackend.domain.broker.client.TossOrderClient.Submission;
import org.example.quantflowbackend.domain.broker.domain.BrokerConnectionStatus;
import org.example.quantflowbackend.domain.broker.domain.BrokerType;
import org.example.quantflowbackend.domain.broker.service.BrokerAccountAccessService;
import org.example.quantflowbackend.domain.broker.service.TossAccountQueryService;
import org.example.quantflowbackend.domain.broker.service.TossTokenService;
import org.example.quantflowbackend.domain.trading.domain.Order;
import org.example.quantflowbackend.domain.trading.domain.OrderEvent;
import org.example.quantflowbackend.domain.trading.domain.OrderEventType;
import org.example.quantflowbackend.domain.trading.domain.OrderSide;
import org.example.quantflowbackend.domain.trading.domain.OrderStatus;
import org.example.quantflowbackend.domain.trading.domain.OrderTriggerType;
import org.example.quantflowbackend.domain.trading.repository.OrderEventRepository;
import org.example.quantflowbackend.domain.trading.repository.OrderRepository;
import org.example.quantflowbackend.domain.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AutoBuyOrderExecutionService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AutoBuyOrderPreparationService preparation;
    private final DailyRiskStateService risks;
    private final BrokerAccountAccessService accountAccess;
    private final TossTokenService tokens;
    private final TossAccountQueryService accountQueries;
    private final TossOrderClient toss;
    private final UserRepository users;
    private final AutomationRepository automations;
    private final OrderRepository orders;
    private final OrderEventRepository events;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Transactional(propagation = Propagation.NEVER)
    public OrderResult executeToday(Long memberId, Long connectionId) {
        var existing = orders.findDailyOrder(connectionId, memberId, now().toLocalDate(),
                OrderSide.BUY, OrderTriggerType.ENTRY_RULE);
        if (existing.isPresent()) {
            var order = existing.get();
            return order.getStatus() == OrderStatus.CREATED
                    ? submitPrepared(memberId, connectionId, order.getOrderId())
                    : OrderResult.from(order);
        }
        return submitPrepared(memberId, connectionId, preparation.prepare(memberId, connectionId));
    }

    @Transactional(propagation = Propagation.NEVER)
    public OrderResult submitPrepared(Long memberId, Long connectionId, Long orderId) {
        var credentials = accountAccess.load(memberId, connectionId);
        var token = tokens.getAccessToken(credentials.clientId(), credentials.clientSecret());
        var transaction = new TransactionTemplate(transactionManager);
        BuyRequest request = transaction.execute(status -> claim(memberId, connectionId, orderId));

        Submission outcome;
        try {
            outcome = toss.submit(token, credentials.accountSeq(), request);
        } catch (RuntimeException unexpected) {
            // The HTTP request may already have reached Toss.
            outcome = Submission.unknown();
        }
        var response = outcome;
        return transaction.execute(status -> recordSubmission(memberId, connectionId, orderId, response));
    }

    @Transactional(readOnly = true)
    public OrderResult getToday(Long memberId, Long connectionId) {
        return OrderResult.from(orders.findDailyOrder(connectionId, memberId, now().toLocalDate(),
                OrderSide.BUY, OrderTriggerType.ENTRY_RULE).orElseThrow(this::notFound));
    }

    @Transactional(readOnly = true)
    public OrderResult get(Long memberId, Long connectionId, Long orderId) {
        return OrderResult.from(orders
                .findByOrderIdAndConnectionConnectionIdAndUserMemberIdAndRecommendationTradingDateIsNotNull(
                        orderId, connectionId, memberId)
                .orElseThrow(this::notFound));
    }

    @Transactional(propagation = Propagation.NEVER)
    public OrderResult refresh(Long memberId, Long connectionId, Long orderId) {
        var current = get(memberId, connectionId, orderId);
        if (current.brokerOrderId() == null) {
            return current;
        }
        var details = accountQueries.read(memberId, connectionId,
                (token, accountSeq) -> toss.details(token, accountSeq, current.brokerOrderId()));
        return new TransactionTemplate(transactionManager).execute(
                status -> reconcile(memberId, connectionId, orderId, details));
    }

    private BuyRequest claim(Long memberId, Long connectionId, Long orderId) {
        users.findByMemberId(memberId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "회원 인증이 필요합니다."));
        var order = lockOrder(memberId, connectionId, orderId);
        if (order.getStatus() != OrderStatus.CREATED) {
            throw conflict("이 주문은 이미 전송을 시도했습니다. 주문 상태를 확인해주세요.");
        }
        var automation = automations.findByConnectionConnectionIdAndUserMemberId(connectionId, memberId)
                .orElseThrow(() -> conflict("이 계좌의 자동매수를 켜주세요."));
        var connection = automation.getConnection();
        var settings = automation.getSettings();
        if (connection.getBroker() != BrokerType.TOSS || connection.getStatus() != BrokerConnectionStatus.CONNECTED
                || automation.getDesiredState() != AutomationDesiredState.RUNNING || automation.isKillSwitchActive()
                || !settings.getSettingsId().equals(order.getSettings().getSettingsId())
                || settings.getStatus() != TradingSettingsStatus.ACTIVE) {
            throw conflict("계좌 상태 또는 자동매수 설정이 변경되었습니다.");
        }

        var risk = risks.lockAndValidate(connectionId, order.getRecommendationTradingDate(), settings).state();
        LocalDateTime now = now();
        if (!now.toLocalDate().equals(order.getRecommendationTradingDate()) || !now.isBefore(order.getExpiresAt())) {
            throw conflict("시세 또는 손익의 유효 시간이 지나 주문을 전송할 수 없습니다.");
        }
        if (!order.getClientOrderKey().matches("[A-Za-z0-9_-]{1,36}")) {
            throw conflict("증권사에 전달할 주문 식별자가 올바르지 않습니다.");
        }

        var request = BuyRequest.limit(order.getClientOrderKey(), order.getStock().getSymbol(),
                order.getQuantity(), order.getLimitPrice());
        order.beginSubmission();
        recordEvent(order, "submit", OrderEventType.SUBMIT, OrderStatus.CREATED, Map.of());
        if (risk != null) {
            risk.recordBuyAttempt();
        }
        return request;
    }

    private OrderResult recordSubmission(Long memberId, Long connectionId, Long orderId, Submission response) {
        var order = lockOrder(memberId, connectionId, orderId);
        var next = switch (response.outcome()) {
            case ACCEPTED -> OrderStatus.ACCEPTED;
            case REJECTED -> OrderStatus.REJECTED;
            case UNKNOWN -> OrderStatus.UNKNOWN;
        };
        order.recordSubmission(next, response.orderId(), response.reason());
        var details = response.reason() == null ? Map.<String, Object>of() : Map.<String, Object>of("reason", response.reason());
        recordEvent(order, "submission-result", OrderEventType.ACK, OrderStatus.SUBMITTING, details);
        return OrderResult.from(order);
    }

    private OrderResult reconcile(Long memberId, Long connectionId, Long orderId, OrderDetails details) {
        var order = lockOrder(memberId, connectionId, orderId);
        if (!matches(order, details) || details.status() == null || details.execution() == null
                || details.execution().filledQuantity() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "토스 주문 상세를 확인할 수 없습니다.");
        }
        var filled = details.execution().filledQuantity().stripTrailingZeros();
        if (filled.signum() < 0 || filled.scale() > 0 || filled.compareTo(order.getQuantity()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "토스 체결 수량을 확인할 수 없습니다.");
        }

        var next = switch (details.status()) {
            case "PENDING" -> filled.signum() == 0 ? OrderStatus.ACCEPTED : OrderStatus.PARTIALLY_FILLED;
            case "PARTIAL_FILLED" -> OrderStatus.PARTIALLY_FILLED;
            case "FILLED" -> OrderStatus.FILLED;
            case "PENDING_CANCEL" -> OrderStatus.CANCEL_PENDING;
            case "CANCELED", "REPLACED" -> OrderStatus.CANCELED;
            case "REJECTED" -> OrderStatus.REJECTED;
            default -> null;
        };
        var previous = order.getStatus();
        if (next != null && order.applyBrokerStatus(next, filled)) {
            recordEvent(order, "reconcile-" + UUID.randomUUID(), OrderEventType.RECONCILE, previous,
                    Map.of("filledQuantity", filled, "brokerStatus", details.status()));
        }
        return OrderResult.from(order);
    }

    private boolean matches(Order order, OrderDetails details) {
        return Objects.equals(order.getBrokerOrderNo(), details.orderId())
                && order.getStock().getSymbol().equals(details.symbol())
                && "BUY".equals(details.side()) && "LIMIT".equals(details.orderType()) && "KRW".equals(details.currency())
                && details.quantity() != null && details.quantity().compareTo(order.getQuantity()) == 0
                && details.price() != null && details.price().compareTo(order.getLimitPrice()) == 0
                && details.orderedAt() != null
                && order.getBrokerOrderDate().equals(details.orderedAt().atZoneSameInstant(SEOUL).toLocalDate());
    }

    private Order lockOrder(Long memberId, Long connectionId, Long orderId) {
        return orders.lockAutoBuyOrder(orderId, connectionId, memberId).orElseThrow(this::notFound);
    }

    private void recordEvent(Order order, String key, OrderEventType type, OrderStatus previous, Map<String, Object> details) {
        var now = now();
        events.save(OrderEvent.create(order, key, type, previous, order.getStatus(), details, now, now));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), SEOUL);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "자동매수 주문을 찾을 수 없습니다.");
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record OrderResult(Long orderId, LocalDate tradingDate, OrderStatus status, BigDecimal quantity,
                              BigDecimal filledQuantity, BigDecimal limitPrice, String brokerOrderId, String reason) {
        static OrderResult from(Order order) {
            return new OrderResult(order.getOrderId(), order.getRecommendationTradingDate(), order.getStatus(),
                    order.getQuantity(), order.getFilledQuantity(), order.getLimitPrice(),
                    order.getBrokerOrderNo(), order.getRejectionReason());
        }
    }
}
