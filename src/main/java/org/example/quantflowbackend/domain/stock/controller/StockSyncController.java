package org.example.quantflowbackend.domain.stock.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.stock.dto.StockSyncResponse;
import org.example.quantflowbackend.domain.stock.service.StockSyncService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Profile("local")
@RestController
@RequestMapping("/api/v1/stocks")
@RequiredArgsConstructor
@Tag(name = "종목 동기화", description = "로컬 개발용 목록 동기화")
@SecurityRequirement(name = "bearerAuth")
public class StockSyncController {
    private final StockSyncService stockSyncService;

    @PostMapping("/sync")
    @Operation(
            summary = "시장별 종목 동기화",
            description = """
                    토스에서 국내 보통주 목록을 조회하여 DB에 추가·갱신합니다.
                    local 프로필에서만 사용할 수 있습니다.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "동기화 성공"),
            @ApiResponse(responseCode = "400", description = "지원하지 않는 시장", content = @Content),
            @ApiResponse(responseCode = "401", description = "서비스 로그인 필요", content = @Content),
            @ApiResponse(responseCode = "409", description = "동기화 실행 중", content = @Content),
            @ApiResponse(responseCode = "422", description = "토스 인증·접근 권한 오류", content = @Content),
            @ApiResponse(responseCode = "502", description = "토스 응답 형식 오류", content = @Content),
            @ApiResponse(responseCode = "503", description = "인증 설정 누락 또는 외부 서비스 사용 불가", content = @Content)
    })
    public ResponseEntity<StockSyncResponse> sync(
            @Parameter(
                    description = "동기화할 시장: KOSPI 또는 KOSDAQ",
                    example = "KOSPI"
            )
            @RequestParam(name = "market") String market
    ) {
        StockSyncResponse response = stockSyncService.sync(market);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(response);
    }
}
