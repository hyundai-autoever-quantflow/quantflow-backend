package org.example.quantflowbackend.domain.news.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.news.dto.NaverNewsResponse;
import org.example.quantflowbackend.domain.news.service.NewsService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
@Tag(name = "뉴스", description = "네이버 뉴스 검색")
@SecurityRequirement(name = "bearerAuth")
public class NewsController {
    private final NewsService newsService;

    @GetMapping
    @Operation(
            summary = "뉴스 검색",
            description = """
                    검색어에 해당하는 네이버 뉴스 검색 결과를 조회합니다.
                    date는 최신순, sim은 정확도순입니다.
                    """
    )
    public ResponseEntity<NaverNewsResponse> search(
            @Parameter(description = "검색어", example = "반도체")
            @RequestParam(name = "query") String query,

            @Parameter(description = "검색 개수: 1~100")
            @RequestParam(name = "display", defaultValue = "10") int display,

            @Parameter(description = "검색 시작 위치: 1~1000")
            @RequestParam(name = "start", defaultValue = "1") int start,

            @Parameter(description = "정렬 방식: date 또는 sim")
            @RequestParam(name = "sort", defaultValue = "date") String sort
    ) {
        NaverNewsResponse response = newsService.search(
                query,
                display,
                start,
                sort
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}
