package org.example.quantflowbackend.domain.recommendation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.recommendation.dto.*;
import org.example.quantflowbackend.domain.recommendation.service.DailyRecommendationService;
import org.example.quantflowbackend.domain.recommendation.service.NewsAnalysisService;
import org.example.quantflowbackend.domain.recommendation.service.SearchedNewsAnalysisService;
import org.springframework.cglib.core.Local;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/analyses")
@RequiredArgsConstructor
@Tag(name = "뉴스 분석", description = "입력한 뉴스의 OpenAI 요약 미리보기")
@SecurityRequirement(name = "bearerAuth")
public class NewsAnalysisController {
    private final NewsAnalysisService newsAnalysisService;
    private final SearchedNewsAnalysisService searchedNewsAnalysisService;
    private final DailyRecommendationService dailyRecommendationService;

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

    @PostMapping(
            value = "/search",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "검색한 뉴스 AI 분석",
            description = """
                네이버 최신 검색 결과 최대 100건을 조회한 뒤,
                지정 기간에 포함된 뉴스를 정리하여 AI로 분석합니다.
                해당 기간의 전체 뉴스 수집을 보장하지 않습니다.
                분석 결과와 사용한 뉴스 목록을 반환합니다.
                """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "분석 결과 또는 대상 뉴스 없음"),
            @ApiResponse(responseCode = "400", description = "검색어·기간 오류", content = @Content),
            @ApiResponse(responseCode = "401", description = "인증 실패", content = @Content),
            @ApiResponse(responseCode = "502", description = "뉴스 응답 처리 실패 또는 AI 텍스트 응답 없음", content = @Content),
            @ApiResponse(responseCode = "503", description = "뉴스 API 사용 불가", content = @Content)
    })
    public ResponseEntity<SearchedNewsAnalysisResponse> analyzeSearchedNews(
            @Valid @RequestBody SearchedNewsAnalysisRequest request
    ) {
        var response = searchedNewsAnalysisService.analyze(request);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @PostMapping("/recommendation")
    @Operation(summary = "뉴스 기반 종목 추천 미리보기")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "추천 종목 또는 추천 보류"),
            @ApiResponse(responseCode = "400", description = "요청 또는 기준일 오류", content = @Content),
            @ApiResponse(responseCode = "401", description = "서비스 로그인 필요", content = @Content),
            @ApiResponse(responseCode = "502", description = "뉴스 또는 AI 응답 처리 실패", content = @Content),
            @ApiResponse(responseCode = "503", description = "기준일 랭킹 미저장 또는 외부 서비스 사용 불가", content = @Content)
    })
    public ResponseEntity<SearchedStockRecommendationResponse> recommend(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @RequestParam("rankingDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate rankingDate,
            @Valid @RequestBody SearchedNewsAnalysisRequest request
    ) {
        Long memberId;
        try {
            memberId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "회원 인증이 필요합니다."
            );
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(searchedNewsAnalysisService.recommend(memberId, rankingDate, request));
    }

    @GetMapping("/recommendation/today")
    @Operation(summary = "오늘 저장된 공통 종목 추천 조회")
    public ResponseEntity<DailyRecommendation> today() {
        var result = dailyRecommendationService.findToday()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "오늘 저장된 추천 결과가 없습니다."
                ));

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(result);
    }
}
