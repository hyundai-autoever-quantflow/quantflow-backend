package org.example.quantflowbackend.domain.stock.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Getter
@Entity
@DynamicUpdate
@Table(
        name = "stocks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stocks_1",
                        columnNames = {"market", "symbol"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_id")
    private Long stockId;

    @Column(name = "market", nullable = false, length = 20)
    private String market;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "dart_corp_code", length = 20)
    private String dartCorpCode;

    @Column(name = "is_tradable", nullable = false)
    private boolean isTradable = false;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "category_codes", nullable = false, columnDefinition = "jsonb")
    private List<String> categoryCodes = new ArrayList<>();

    @Column(name = "instrument_type", nullable = false, length = 30)
    private String instrumentType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "market_flags", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> marketFlags = new HashMap<>();

    @Column(name = "market_status_at")
    private LocalDateTime marketStatusAt;

    @Builder
    private Stock(
            String market,
            String symbol,
            String companyName,
            String dartCorpCode,
            String instrumentType
    ) {
        this.market = Objects.requireNonNull(market, "필수 값입니다: market");
        this.symbol = Objects.requireNonNull(symbol, "필수 값입니다: symbol");
        this.companyName = Objects.requireNonNull(companyName, "필수 값입니다: companyName");
        this.dartCorpCode = dartCorpCode;
        this.instrumentType = Objects.requireNonNull(instrumentType, "필수 값입니다: instrumentType");
    }

    public static Stock create(
            String market,
            String symbol,
            String companyName,
            String dartCorpCode,
            String instrumentType
    ) {
        return Stock.builder()
                .market(market)
                .symbol(symbol)
                .companyName(companyName)
                .dartCorpCode(dartCorpCode)
                .instrumentType(instrumentType)
                .build();
    }

    public void updateListingInfo(
            String companyName,
            String instrumentType,
            boolean commonShare,
            String isinCode,
            LocalDateTime checkedAt
    ) {
        this.companyName = Objects.requireNonNull(companyName);
        this.instrumentType = Objects.requireNonNull(instrumentType);

        Map<String, Object> flags = new HashMap<>(this.marketFlags);
        flags.put("isCommonShare", commonShare);
        flags.put("listingProvider", "TOSS");

        if (isinCode != null && !isinCode.isBlank()) {
            flags.put("isinCode", isinCode);
        } else {
            flags.remove("isinCode");
        }

        this.marketFlags = flags;
        this.isTradable = true;
        this.marketStatusAt = Objects.requireNonNull(checkedAt);
    }

    public void deactivateListing(LocalDateTime checkedAt) {
        this.isTradable = false;
        this.marketStatusAt = Objects.requireNonNull(checkedAt);
    }
}
