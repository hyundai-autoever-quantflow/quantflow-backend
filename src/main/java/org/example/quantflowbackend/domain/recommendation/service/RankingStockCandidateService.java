package org.example.quantflowbackend.domain.recommendation.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.broker.client.TossRankingClient;
import org.example.quantflowbackend.domain.broker.dto.TossInvestorRankingResponse;
import org.example.quantflowbackend.domain.broker.dto.TossInvestorRankingResponse.NetBuyItem;
import org.example.quantflowbackend.domain.broker.dto.TossMarketCalendarResponse;
import org.example.quantflowbackend.domain.broker.dto.TossRankingRequest;
import org.example.quantflowbackend.domain.broker.dto.TossRankingResponse;
import org.example.quantflowbackend.domain.broker.service.TossTokenService;
import org.example.quantflowbackend.domain.recommendation.dto.RankedStockCandidate;
import org.example.quantflowbackend.domain.recommendation.dto.RankingSnapshot;
import org.example.quantflowbackend.domain.recommendation.repository.RankingSnapshotRepository;
import org.example.quantflowbackend.domain.stock.domain.Stock;
import org.example.quantflowbackend.domain.stock.repository.StockRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankingStockCandidateService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final List<String> MARKETS = List.of("KOSPI", "KOSDAQ");
    private static final int MAX_CANDIDATES = 5;

    private final StockRepository stockRepository;
    private final TossRankingClient tossRankingClient;
    private final TossTokenService tossTokenService;
    private final RankingSnapshotRepository snapshotRepository;

    @Value("${app.stock-sync.client-id:}")
    private String clientId;

    @Value("${app.stock-sync.client-secret:}")
    private String clientSecret;

    public List<RankedStockCandidate> select(
            LocalDate rankingDate,
            TossRankingResponse volume,
            TossInvestorRankingResponse investor
    ) {
        return select(rankingDate, volume, investor, null);
    }

    private List<RankedStockCandidate> select(
            LocalDate rankingDate, TossRankingResponse volume,
            TossInvestorRankingResponse investor, String categoryCode
    ) {
        validateRankingDates(rankingDate, volume, investor);

        var foreign = bySymbol(investor.foreigner().netBuyRankings());
        var institution = bySymbol(investor.institution().netBuyRankings());

        var matchingRanks = volume.rankings().stream()
                .filter(item -> item.tradingVolume().signum() > 0)
                .filter(item -> foreign.containsKey(item.symbol()))
                .filter(item -> institution.containsKey(item.symbol()))
                .sorted(Comparator.comparingInt(TossRankingResponse.Ranking::rank))
                .toList();

        if (matchingRanks.isEmpty()) {
            return List.of();
        }

        var symbols = matchingRanks.stream()
                .map(TossRankingResponse.Ranking::symbol)
                .distinct()
                .toList();

        var listedStocks = stockRepository.findAllByMarketInAndSymbolIn(MARKETS, symbols)
                .stream()
                .filter(Stock::isTradable)
                .filter(stock -> "STOCK".equals(stock.getInstrumentType()))
                .filter(stock -> Boolean.TRUE.equals(stock.getMarketFlags().get("isCommonShare")))
                .collect(Collectors.groupingBy(Stock::getSymbol));

        List<RankedStockCandidate> candidates = new ArrayList<>();

        for (var rank : matchingRanks) {
            String symbol = rank.symbol();
            var matches = listedStocks.getOrDefault(symbol, List.of());
            if (matches.size() != 1) {
                continue;
            }

            Stock stock = matches.get(0);
            if (categoryCode != null && !stock.getCategoryCodes().contains(categoryCode)) {
                continue;
            }
            candidates.add(new RankedStockCandidate(
                    stock.getStockId(),
                    stock.getMarket(),
                    symbol,
                    stock.getCompanyName(),
                    rank.tradingVolume(),
                    foreign.get(symbol).netBuyAmount(),
                    institution.get(symbol).netBuyAmount()
            ));

            if (candidates.size() == MAX_CANDIDATES) {
                break;
            }
        }

        return List.copyOf(candidates);
    }

    public TossMarketCalendarResponse marketCalendar(LocalDate date) {
        return tossRankingClient.getMarketCalendar(accessToken(), date);
    }

    public RankingSnapshot captureToday() {
        OffsetDateTime now = OffsetDateTime.now(SEOUL);

        if (now.toLocalTime().isBefore(LocalTime.of(21, 0))) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "랭킹은 한국 시간 오후 9시 이후에 수집합니다."
            );
        }

        String token = accessToken();

        var volume = tossRankingClient.getRankings(token, new TossRankingRequest(
                TossRankingRequest.Type.MARKET_TRADING_VOLUME,
                TossRankingRequest.MarketCountry.KR,
                "1d"
        ));
        var investor = tossRankingClient.getInvestorRankings(token);

        LocalDate rankingDate = now.toLocalDate();
        validateRankingDates(rankingDate, volume, investor);

        var snapshot = new RankingSnapshot(
                rankingDate, OffsetDateTime.now(SEOUL), volume, investor
        );
        snapshotRepository.save(snapshot);
        return snapshot;
    }

    public List<RankedStockCandidate> load(LocalDate rankingDate) {
        return load(rankingDate, null);
    }

    public List<RankedStockCandidate> load(LocalDate rankingDate, String categoryCode) {
        if (rankingDate == null || !rankingDate.isBefore(LocalDate.now(SEOUL))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "오늘보다 이전인 랭킹 기준일을 입력해주세요."
            );
        }

        var snapshot = snapshotRepository.find(rankingDate)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        rankingDate + "에 저장된 랭킹이 없습니다."
                ));
        return select(rankingDate, snapshot.volume(), snapshot.investor(), categoryCode);
    }

    private void validateRankingDates(
            LocalDate rankingDate,
            TossRankingResponse volume,
            TossInvestorRankingResponse investor
    ) {
        if (!matchesDate(volume.rankedAt(), rankingDate)
                || !matchesDate(investor.foreigner().rankedAt(), rankingDate)
                || !matchesDate(investor.institution().rankedAt(), rankingDate)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "요청한 거래일의 거래량·외국인·기관 랭킹을 모두 확인할 수 없습니다."
            );
        }
    }

    private boolean matchesDate(OffsetDateTime rankedAt, LocalDate expectedDate) {
        return rankedAt != null
                && rankedAt.atZoneSameInstant(SEOUL).toLocalDate().equals(expectedDate);
    }

    private Map<String, NetBuyItem> bySymbol(List<NetBuyItem> items) {
        return items.stream().collect(
                Collectors.toMap(NetBuyItem::symbol, Function.identity())
        );
    }

    private String accessToken() {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "토스 조회용 인증정보가 설정되지 않았습니다."
            );
        }
        return tossTokenService.getAccessToken(clientId, clientSecret);
    }
}
