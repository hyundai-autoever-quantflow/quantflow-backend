package org.example.quantflowbackend.domain.stock.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.broker.dto.TossListedStock;
import org.example.quantflowbackend.domain.stock.domain.Stock;
import org.example.quantflowbackend.domain.stock.repository.StockRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockCatalogWriter {
    private final StockRepository stockRepository;

    @Transactional
    public int upsert(
            String market,
            List<TossListedStock> listings,
            LocalDateTime checkedAt
    ) {
        validateListings(listings);

        Map<String, Stock> existing = stockRepository
                .findAllByMarket(market)
                .stream()
                .collect(Collectors.toMap(
                        Stock::getSymbol,
                        Function.identity()
                ));

        for (TossListedStock listing : listings) {
            Stock stock = existing.get(listing.symbol());

            if (stock == null) {
                stock = Stock.create(
                        market,
                        listing.symbol(),
                        listing.name(),
                        null,
                        listing.securityType()
                );
            }
            stock.updateListingInfo(
                    listing.name(),
                    listing.securityType(),
                    Boolean.TRUE.equals(listing.isCommonShare()),
                    listing.isinCode(),
                    checkedAt
            );

            stockRepository.save(stock);
        }

        Set<String> receivedSymbols = listings.stream()
                .map(TossListedStock::symbol)
                .collect(Collectors.toSet());

        for (Stock stock : existing.values()) {
            boolean managedByThisSync =
                    "TOSS".equals(stock.getMarketFlags().get("listingProvider"))
                            && "STOCK".equals(stock.getInstrumentType())
                            && Boolean.TRUE.equals(
                            stock.getMarketFlags().get("isCommonShare")
                    );

            if (managedByThisSync
                    && !receivedSymbols.contains(stock.getSymbol())) {
                stock.deactivateListing(checkedAt);
            }
        }

        return listings.size();
    }

    private void validateListings(List<TossListedStock> listings) {
        if (listings == null || listings.isEmpty()) {
            throw invalidResponse("종목 목록이 비어 있어 갱신하지 않았습니다");
        }
        Set<String> symbols = new HashSet<>();

        for (TossListedStock listing : listings) {
            if (listing == null
                    || listing.symbol() == null
                    || listing.symbol().isBlank()
                    || !symbols.add(listing.symbol())) {
                throw invalidResponse("종목 코드가 누락되거나 중복되었습니다");
            }
        }
    }

    private ResponseStatusException invalidResponse(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                message
        );
    }
}
