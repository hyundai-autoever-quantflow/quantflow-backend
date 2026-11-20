package org.example.quantflowbackend.domain.trading.repository;

import org.example.quantflowbackend.domain.trading.domain.OrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {
}
