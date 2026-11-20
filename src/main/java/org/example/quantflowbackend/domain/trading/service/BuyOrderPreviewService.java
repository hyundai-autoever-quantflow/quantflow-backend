package org.example.quantflowbackend.domain.trading.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class BuyOrderPreviewService {
    private final BuyOrderContextService contexts;

    public PreviewResponse preview(Long memberId, Long connectionId) {
        var context = contexts.load(memberId, connectionId);
        var stock = context.stock();
        var settings = context.settings();
        var quote = context.quote();

        BigDecimal budget = context.usableBudgetKrw();
        BigDecimal quantity = budget.divide(quote.lastPrice(), 0, RoundingMode.DOWN);
        return new PreviewResponse(
                context.tradingDate(), settings.settingsId(), stock.getMarket(),
                stock.getSymbol(), stock.getCompanyName(), settings.entryBudgetKrw(),
                context.availableCashKrw(), budget, quote.lastPrice(), quote.timestamp(),
                quantity, quantity.multiply(quote.lastPrice())
        );
    }

    public record PreviewResponse(
            LocalDate tradingDate,
            Long settingsId,
            String market,
            String symbol,
            String companyName,
            BigDecimal configuredBudgetKrw,
            BigDecimal cashBuyingPowerKrw,
            BigDecimal usableBudgetKrw,
            BigDecimal referencePriceKrw,
            Instant priceTimestamp,
            BigDecimal estimatedQuantity,
            BigDecimal estimatedAmountKrw
    ) {}
}
