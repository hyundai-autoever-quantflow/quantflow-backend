package org.example.quantflowbackend.domain.broker.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "보유 자산 현황. 금액·수량·비율은 정밀도 보존을 위해 소수 문자열로 반환합니다.")
public record HoldingsOverview(
        @NotNull @Valid @Schema(description = "통화별 총 매입금액") CurrencyAmounts totalPurchaseAmount,
        @NotNull @Valid OverviewMarketValue marketValue,
        @NotNull @Valid OverviewProfitLoss profitLoss,
        @NotNull @Valid OverviewDailyProfitLoss dailyProfitLoss,
        @NotNull @Valid @Schema(description = "보유 종목. 미보유 시 빈 배열") List<@NotNull @Valid Holding> items
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "통화별 금액. KRW와 USD를 합산하거나 서버에서 환산하지 않습니다.")
    public record CurrencyAmounts(
            @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(description = "국내 종목 합계. 해당 종목이 없으면 0", type = "string", format = "decimal")
            BigDecimal krw,
            @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(description = "미국 종목 합계. 해당 종목이 없으면 null일 수 있습니다.", type = "string", format = "decimal", nullable = true)
            BigDecimal usd
    ) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "통화별 평가금액")
    public record OverviewMarketValue(
            @NotNull @Valid @Schema(description = "비용 공제 전 평가금액")
            CurrencyAmounts amount,
            @NotNull @Valid @Schema(description = "비용 공제 후 평가금액")
            CurrencyAmounts amountAfterCost
    ) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "계좌 손익. 전체 손익률은 토스가 제공한 원화 환산 기준 값입니다.")
    public record OverviewProfitLoss(
            @NotNull @Valid @Schema(description = "통화별 손익금액")
            CurrencyAmounts amount,
            @NotNull @Valid @Schema(description = "비용 공제 후 손익금액")
            CurrencyAmounts amountAfterCost,
            @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(description = "손익률 소수비율. 0.1077은 10.77%", type = "string", format = "decimal")
            BigDecimal rate,
            @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(description = "비용 공제 후 손익률 소수비율", type = "string", format = "decimal")
            BigDecimal rateAfterCost
    ) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "계좌 일간 손익")
    public record OverviewDailyProfitLoss(
            @NotNull @Valid @Schema(description = "통화별 일간 손익금액")
            CurrencyAmounts amount,
            @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(description = "일간 손익률 소수비율", type = "string", format = "decimal")
            BigDecimal rate
    ) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "보유 종목. 각 금액은 currency 기준이며 수량은 소수일 수 있습니다.")
    public record Holding(
            @NotBlank @Schema(description = "종목 코드 또는 티커")
            String symbol,
            @NotBlank @Schema(description = "종목명")
            String name,
            @NotBlank @Schema(description = "시장 국가. 현재 KR 또는 US")
            String marketCountry,
            @NotBlank @Schema(description = "거래 통화. 현재 KRW 또는 USD")
            String currency,
            @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(description = "보유 수량. 소수 수량 지원", type = "string", format = "decimal")
            BigDecimal quantity,
            @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(description = "현재가", type = "string", format = "decimal")
            BigDecimal lastPrice,
            @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(description = "평균 매수가", type = "string", format = "decimal")
            BigDecimal averagePurchasePrice,
            @NotNull @Valid @Schema(description = "종목 평가금액")
            MarketValue marketValue,
            @NotNull @Valid @Schema(description = "종목 손익")
            ProfitLoss profitLoss,
            @NotNull @Valid @Schema(description = "종목 일간 손익")
            DailyProfitLoss dailyProfitLoss,
            @NotNull @Valid @Schema(description = "거래 비용")
            Cost cost
    ) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "종목 매입·평가금액")
    public record MarketValue(
            @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(description = "매입금액", type = "string", format = "decimal")
            BigDecimal purchaseAmount,
            @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(description = "평가금액", type = "string", format = "decimal")
            BigDecimal amount,
            @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(description = "비용 공제 후 평가금액", type = "string", format = "decimal")
            BigDecimal amountAfterCost
    ) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "종목 손익. 비율은 백분율로 변환하지 않은 원본 소수비율입니다.")
    public record ProfitLoss(
            @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(description = "손익금액", type = "string", format = "decimal")
            BigDecimal amount,
            @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(description = "비용 공제 후 손익금액", type = "string", format = "decimal")
            BigDecimal amountAfterCost,
            @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(description = "손익률. 0.1077은 10.77%", type = "string", format = "decimal")
            BigDecimal rate,
            @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(description = "비용 공제 후 손익률", type = "string", format = "decimal")
            BigDecimal rateAfterCost
    ) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "종목 일간 손익")
    public record DailyProfitLoss(
            @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(description = "일간 손익금액", type = "string", format = "decimal")
            BigDecimal amount,
            @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(description = "일간 손익률 소수비율", type = "string", format = "decimal")
            BigDecimal rate
    ) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "종목 거래 비용")
    public record Cost(
            @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(description = "수수료", type = "string", format = "decimal")
            BigDecimal commission,
            @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(description = "세금. 없으면 null", type = "string", format = "decimal", nullable = true)
            BigDecimal tax
    ) { }

}
