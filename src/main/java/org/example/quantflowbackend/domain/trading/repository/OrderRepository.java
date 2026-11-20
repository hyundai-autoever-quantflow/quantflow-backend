package org.example.quantflowbackend.domain.trading.repository;

import jakarta.persistence.LockModeType;
import org.example.quantflowbackend.domain.trading.domain.Order;
import org.example.quantflowbackend.domain.trading.domain.OrderSide;
import org.example.quantflowbackend.domain.trading.domain.OrderTriggerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    boolean existsByConnectionConnectionIdAndRecommendationTradingDateAndSideAndTriggerType(
            Long connectionId, LocalDate recommendationTradingDate, OrderSide side, OrderTriggerType triggerType
    );

    Optional<Order> findByOrderIdAndConnectionConnectionIdAndUserMemberIdAndRecommendationTradingDateIsNotNull(
            Long orderId, Long connectionId, Long memberId
    );

    @Query("""
            SELECT o FROM Order o
            WHERE o.connection.connectionId = :connectionId AND o.user.memberId = :memberId
                AND o.recommendationTradingDate = :date AND o.side = :side AND o.triggerType = :trigger
            """)
    Optional<Order> findDailyOrder(@Param("connectionId") Long connectionId, @Param("memberId") Long memberId,
                                   @Param("date") LocalDate date, @Param("side") OrderSide side,
                                   @Param("trigger") OrderTriggerType trigger);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o FROM Order o
            WHERE o.orderId = :orderId AND o.connection.connectionId = :connectionId
                AND o.user.memberId = :memberId AND o.recommendationTradingDate IS NOT NULL
            """)
    Optional<Order> lockAutoBuyOrder(@Param("orderId") Long orderId, @Param("connectionId") Long connectionId,
                                    @Param("memberId") Long memberId);
}
