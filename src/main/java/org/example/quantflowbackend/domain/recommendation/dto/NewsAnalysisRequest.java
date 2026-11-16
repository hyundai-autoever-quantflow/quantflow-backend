package org.example.quantflowbackend.domain.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "요약할 뉴스. 긴 본문을 URL 쿼리가 아닌 JSON body로 전달합니다.")
public record NewsAnalysisRequest(
        @NotBlank @Size(max = 500)
        @Schema(description = "뉴스 제목. 최대 500자", example = "신규 공장 건설 계획 발표") String title,
        @NotBlank @Size(max = 50000)
        @Schema(description = "뉴스 본문. 최대 50,000자", example = "한 기업이 신규 공장 건설 계획을 발표했다. 구체적인 일정은 공개하지 않았다.") String content
) { }
