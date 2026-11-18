package org.example.quantflowbackend.domain.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record SearchedNewsAnalysisRequest(
        @NotBlank
        @Size(max = 200)
        @Schema(description = "뉴스 검색어", example = "반도체")
        String query,

        @NotNull
        @Schema(
                description = "분석 시작 시각: 포함",
                example = "2026-11-16T08:00:00+09:00"
        )
        OffsetDateTime from,

        @NotNull
        @Schema(
                description = "분석 종료 시각: 제외",
                example = "2026-11-17T08:00:00+09:00"
        )
        OffsetDateTime to
) {
}
