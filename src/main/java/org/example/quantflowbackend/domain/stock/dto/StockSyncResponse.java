package org.example.quantflowbackend.domain.stock.dto;

import java.time.LocalDateTime;

public record StockSyncResponse(
        String market,
        int processedCount,
        LocalDateTime checkedAt
) {
}