package org.example.quantflowbackend.domain.recommendation.dto;

import java.math.BigDecimal;

public record RankedStockCandidate(
        Long stockId,
        String market,
        String symbol,
        String companyName,
        BigDecimal tradingVolume,
        BigDecimal foreignNetBuyAmountKrw,
        BigDecimal institutionNetBuyAmountKrw
) {
}
