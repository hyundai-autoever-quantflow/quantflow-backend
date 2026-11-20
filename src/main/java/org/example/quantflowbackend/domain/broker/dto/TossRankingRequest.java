package org.example.quantflowbackend.domain.broker.dto;


public record TossRankingRequest(
    Type type,
    MarketCountry marketCountry,
    String duration
) {
    public enum Type {
        MARKET_TRADING_AMOUNT,
        MARKET_TRADING_VOLUME,
        TOP_GAINERS,
        TOP_LOSERS,
        TOSS_SECURITIES_TRADING_AMOUNT,
        TOSS_SECURITIES_TRADING_VOLUME
    }

    public enum MarketCountry {
        KR,
        US
    }
}
