package org.example.quantflowbackend.domain.recommendation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.recommendation.service.RankingStockCandidateService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Profile("local")
@RestController
@RequiredArgsConstructor
@Tag(name = "랭킹 수집")
@SecurityRequirement(name = "bearerAuth")
public class RankingSnapshotController {
    private final RankingStockCandidateService candidateService;

    @PostMapping("/api/v1/ranking-snapshots")
    @Operation(
            summary = "오늘의 토스 랭킹 저장",
            description = "한국 시간 오후 9시 이후에 세 랭킹을 조회해 거래일별로 저장합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(responseCode = "401", description = "서비스 로그인 필요", content = @Content),
            @ApiResponse(responseCode = "409", description = "수집 가능 시각 이전", content = @Content),
            @ApiResponse(responseCode = "422", description = "토스 인증 또는 접근 권한 오류", content = @Content),
            @ApiResponse(responseCode = "502", description = "토스 응답 형식 오류", content = @Content),
            @ApiResponse(responseCode = "503", description = "랭킹 미준비 또는 조회·저장 서비스 사용 불가", content = @Content)
    })
    public ResponseEntity<CaptureResponse> capture() {
        var snapshot = candidateService.captureToday();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CaptureResponse(snapshot.rankingDate(), snapshot.collectedAt()));
    }

    public record CaptureResponse(LocalDate rankingDate, OffsetDateTime collectedAt) {}
}
