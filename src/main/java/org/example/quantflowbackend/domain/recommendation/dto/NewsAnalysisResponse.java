package org.example.quantflowbackend.domain.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "뉴스 요약 결과. DB 저장이나 주문 실행은 수행하지 않습니다.")
public record NewsAnalysisResponse(
        @Schema(description = "AI가 반환한 뉴스 요약") String summary
) { }
