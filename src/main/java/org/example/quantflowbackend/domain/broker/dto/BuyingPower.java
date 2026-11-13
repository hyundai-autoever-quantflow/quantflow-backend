package org.example.quantflowbackend.domain.broker.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "현금 기반 매수 가능 금액. 미수거래를 제외하며 환율 환산이나 예산 예약을 수행하지 않습니다.")
public record BuyingPower(
        @NotBlank @Schema(description = "조회 통화", example = "KRW") String currency,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING)
        @Schema(description = "현금 기반 매수 가능 금액. 0도 정상 응답", type = "string", format = "decimal", example = "5000000")
        BigDecimal cashBuyingPower
) { }
