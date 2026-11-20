package org.example.quantflowbackend.domain.trading.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.automation.domain.AutomationDesiredState;
import org.example.quantflowbackend.domain.automation.domain.TradingSettingsStatus;
import org.example.quantflowbackend.domain.automation.service.AutomationService;
import org.example.quantflowbackend.domain.broker.service.BrokerTradingQueryService;
import org.example.quantflowbackend.domain.recommendation.service.DailyRecommendationService;
import org.example.quantflowbackend.domain.stock.domain.Stock;
import org.example.quantflowbackend.domain.stock.repository.StockRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class BuyOrderPreviewService {
    private final AutomationService automations;
    private final DailyRecommendationService recommendations;
    private final StockRepository stocks;
    private final BrokerTradingQueryService tradingQueries;

    public PreviewResponse preview(Long memberId, Long connectionId) {
        var automation = automations.get(memberId, connectionId);
        var setting = automation.settings();
        if (automation.desiredState() != AutomationDesiredState.RUNNING
                || automation.killSwitchActive() || setting == null) {
            throw conflict("이 계좌의 자동매수를 켜주세요.");
        }

        var recommendation = recommendations.findToday()
                .orElseThrow(() -> conflict("오늘 저장된 추천이 없습니다."));
        var pick = recommendation.stock();
        if (pick == null) {
            throw conflict("오늘의 종목 추천이 보류되었습니다.");
        }
        if (setting.status() != TradingSettingsStatus.ACTIVE
                || setting.effectiveTradingDate().isAfter(recommendation.tradingDate())) {
            throw conflict("오늘 적용할 수 있는 매수 설정이 필요합니다.");
        }
        if (pick.market() == null || pick.symbol() == null || pick.symbol().isBlank()) {
            throw conflict("추천 종목의 시장 또는 종목 코드가 올바르지 않습니다.");
        }

        var stock = stocks.findByMarketAndSymbol(pick.market().name(), pick.symbol())
                .filter(Stock::isTradable)
                .filter(item -> "STOCK".equals(item.getInstrumentType()))
                .filter(item -> "TOSS".equals(item.getMarketFlags().get("listingProvider"))
                        && Boolean.TRUE.equals(item.getMarketFlags().get("isCommonShare")))
                .orElseThrow(() -> conflict("추천 종목이 현재 토스 매수 대상 목록에 없습니다."));

        var buyingPower = tradingQueries.getBuyingPower(memberId, connectionId, "KRW");
        BigDecimal availableCash = buyingPower.buyingPower().cashBuyingPower();
        var price = tradingQueries.getPrices(memberId, connectionId, stock.getSymbol())
                .prices().stream()
                .filter(quote -> stock.getSymbol().equals(quote.symbol()))
                .filter(quote -> "KRW".equals(quote.currency()) && quote.lastPrice().signum() > 0)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY, "추천 종목의 원화 현재가를 확인할 수 없습니다."
                ));

        BigDecimal budget = setting.entryBudgetKrw().min(availableCash).max(BigDecimal.ZERO);
        BigDecimal quantity = budget.divide(price.lastPrice(), 0, RoundingMode.DOWN);
        return new PreviewResponse(
                recommendation.tradingDate(), setting.settingsId(), stock.getMarket(),
                stock.getSymbol(), stock.getCompanyName(), setting.entryBudgetKrw(), availableCash,
                budget, price.lastPrice(), price.timestamp(), quantity, quantity.multiply(price.lastPrice())
        );
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
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
