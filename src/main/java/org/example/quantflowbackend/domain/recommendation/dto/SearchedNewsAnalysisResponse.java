package org.example.quantflowbackend.domain.recommendation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.quantflowbackend.domain.news.dto.PreparedNews;

import java.util.List;

@Schema(description = "최신 검색 결과 최대 100건에서 기간에 맞는 뉴스를 분석한 결과")
public record SearchedNewsAnalysisResponse(
        String query,

        @Schema(description = "정리하기 전 조회한 뉴스 건수")
        int fetchedCount,

        @Schema(description = "AI 분석에 사용한 뉴스. 배열 순서가 근거 번호 순서입니다.")
        List<PreparedNews> news,

        @Schema(description = "AI 분석 결과 또는 분석 대상 없음 안내")
        String analysis
) {
}
