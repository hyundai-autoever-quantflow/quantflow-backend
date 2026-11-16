package org.example.quantflowbackend.domain.recommendation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.recommendation.dto.NewsAnalysisRequest;
import org.example.quantflowbackend.domain.recommendation.dto.NewsAnalysisResponse;
import org.example.quantflowbackend.domain.recommendation.service.NewsAnalysisService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analyses")
@RequiredArgsConstructor
@Tag(name = "뉴스 분석", description = "입력한 뉴스의 OpenAI 요약 미리보기")
@SecurityRequirement(name = "bearerAuth")
public class NewsAnalysisController {
    private final NewsAnalysisService newsAnalysisService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "뉴스 요약", description = """
            서비스 Access Token으로 인증한 뒤 title과 content를 JSON body로 전달합니다.
            뉴스 본문을 URL 쿼리로 보내지 않습니다. 제목은 최대 500자, 본문은 최대 50,000자이며 빈 값은 허용하지 않습니다.
            요약 결과를 반환하며 DB 저장이나 주문 실행은 하지 않습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "요약 성공"),
            @ApiResponse(responseCode = "400", description = "제목·본문 누락, 빈 값, 길이 초과 또는 JSON 형식 오류", content = @Content),
            @ApiResponse(responseCode = "401", description = "서비스 Access Token 인증 실패", content = @Content),
            @ApiResponse(responseCode = "502", description = "AI 텍스트 응답 없음", content = @Content)
    })
    public ResponseEntity<NewsAnalysisResponse> summarize(@Valid @RequestBody NewsAnalysisRequest request) {
        String summary = newsAnalysisService.summarize(request.title(), request.content());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new NewsAnalysisResponse(summary));
    }
}
