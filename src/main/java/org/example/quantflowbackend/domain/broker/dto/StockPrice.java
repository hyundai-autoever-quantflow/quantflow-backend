package org.example.quantflowbackend.domain.broker.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "토스 종목 현재가. 미체결 등으로 timestamp가 없을 수 있으며 실시간 체결 가능 가격을 보장하지 않습니다.")
public record StockPrice(
        @NotBlank @Schema(description = "종목 코드 또는 티커", example = "005930") String symbol,
        @Schema(description = "토스가 제공한 가격 데이터 시각. UTC로 반환하며 없으면 null", nullable = true)
        Instant timestamp,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(description = "거래 통화 기준 현재가", type = "string", format = "decimal", example = "72000") BigDecimal lastPrice,
        @NotBlank @Schema(description = "거래 통화. 현재 KRW 또는 USD", example = "KRW") String currency
) { }
