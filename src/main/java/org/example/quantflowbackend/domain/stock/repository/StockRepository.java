package org.example.quantflowbackend.domain.stock.repository;

import org.example.quantflowbackend.domain.stock.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    List<Stock> findAllByMarket(String market);

    Optional<Stock> findByMarketAndSymbol(String market, String symbol);
}
