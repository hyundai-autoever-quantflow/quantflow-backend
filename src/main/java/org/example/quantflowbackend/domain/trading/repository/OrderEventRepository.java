package org.example.quantflowbackend.domain.trading.repository;

import org.example.quantflowbackend.domain.trading.domain.OrderEvent;
import org.example.quantflowbackend.domain.trading.domain.OrderEventType;
import org.example.quantflowbackend.domain.trading.domain.OrderSide;
import org.example.quantflowbackend.domain.trading.domain.OrderTriggerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {
    @Query("""
            SELECT COUNT(DISTINCT o.orderId)
            FROM OrderEvent e JOIN e.order o
            WHERE o.connection.connectionId = :connectionId AND o.recommendationTradingDate = :date
                AND o.side = :side AND o.triggerType = :trigger AND e.eventType = :eventType
            """)
    long countDailyOrderAttempts(@Param("connectionId") Long connectionId, @Param("date") LocalDate date,
                                @Param("side") OrderSide side, @Param("trigger") OrderTriggerType trigger,
                                @Param("eventType") OrderEventType eventType);
}
