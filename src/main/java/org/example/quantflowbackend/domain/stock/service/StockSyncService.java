package org.example.quantflowbackend.domain.stock.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.broker.client.TossStockClient;
import org.example.quantflowbackend.domain.broker.dto.TossListedStock;
import org.example.quantflowbackend.domain.broker.exception.TossAuthenticationException;
import org.example.quantflowbackend.domain.broker.service.TossTokenService;
import org.example.quantflowbackend.domain.stock.dto.StockSyncResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
public class StockSyncService {
    private static final Set<String> SUPPORTED_MARKETS =
            Set.of("KOSPI", "KOSDAQ");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final TossTokenService tossTokenService;
    private final TossStockClient tossStockClient;
    private final StockCatalogWriter stockCatalogWriter;

    private final ReentrantLock syncLock = new ReentrantLock();

    @Value("${app.stock-sync.client-id:}")
    private String clientId;

    @Value("${app.stock-sync.client-secret:}")
    private String clientSecret;

    public StockSyncResponse sync(String market) {
        validate(market);

        if (!syncLock.tryLock()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "종목 동기화가 이미 진행 중입니다."
            );
        }

        try {
            List<TossListedStock> listings = fetchListings(market);

            LocalDateTime checkedAt = LocalDateTime.now(SEOUL);

            int processedCount = stockCatalogWriter.upsert(
                    market,
                    listings,
                    checkedAt
            );

            return new StockSyncResponse(
                    market,
                    processedCount,
                    checkedAt
            );
        } finally {
            syncLock.unlock();
        }
    }

    private List<TossListedStock> fetchListings(String market) {
        String accessToken = tossTokenService.getAccessToken(
                clientId,
                clientSecret
        );

        try {
            return tossStockClient.getCommonStocks(
                    accessToken,
                    market
            );
        } catch (TossAuthenticationException exception) {
            tossTokenService.invalidate(clientId, clientSecret);

            String renewedToken = tossTokenService.getAccessToken(
                    clientId,
                    clientSecret
            );

            return tossStockClient.getCommonStocks(
                    renewedToken,
                    market
            );
        }
    }



    private void validate(String market) {
        if (market == null || !SUPPORTED_MARKETS.contains(market)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "동기화 시장은 KOSPI 또는 KOSDAQ이어야 합니다."
            );
        }

        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "종목 동기화용 토스 인증정보가 설정되지 않았습니다."
            );
        }
    }
}
