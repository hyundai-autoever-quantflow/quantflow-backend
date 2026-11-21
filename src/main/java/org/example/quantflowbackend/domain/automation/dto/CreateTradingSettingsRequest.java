package org.example.quantflowbackend.domain.automation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateTradingSettingsRequest(
        @NotNull @Positive @Digits(integer = 20, fraction = 0)
        BigDecimal entryBudgetKrw,

        @NotNull @Positive @Digits(integer = 5, fraction = 0) @Max(Short.MAX_VALUE)
        @Schema(type = "integer", minimum = "1", maximum = "32767", example = "1")
        BigDecimal maxBuysPerDay,

        @Positive @Digits(integer = 20, fraction = 0)
        @Schema(description = "일일 계좌 손실 한도(원). 생략하거나 null이면 매수 예산과 횟수를 적용합니다.",
                nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        BigDecimal dailyLossLimitKrw
) {}
