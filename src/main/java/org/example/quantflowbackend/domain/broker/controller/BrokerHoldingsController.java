package org.example.quantflowbackend.domain.broker.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.broker.dto.BrokerHoldingsResponse;
import org.example.quantflowbackend.domain.broker.service.BrokerHoldingsService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/broker-connections")
@RequiredArgsConstructor
@Tag(name = "보유 주식", description = "연결된 토스증권 실계좌의 보유 주식 조회")
@SecurityRequirement(name = "bearerAuth")
public class BrokerHoldingsController {
    private final BrokerHoldingsService service;

    @GetMapping("/{connectionId}/holdings")
    @Operation(summary = "내 계좌 보유 주식 조회", description = """
            내 연결 목록에서 받은 connectionId로 보유 주식을 조회합니다. 토스 accountSeq를 입력하는 API가 아닙니다.
            본인 소유의 CONNECTED 상태 토스 계좌만 조회할 수 있습니다. symbol 생략 시 전체, 지정 시 해당 종목과 재계산된 요약을 반환합니다.
            금액·수량·비율은 소수 문자열입니다. KRW/USD 합계는 별개이며, 손익률 0.1077은 10.77%입니다.
            미국 종목이 없으면 요약 usd와 세금 tax는 null일 수 있습니다. 미보유 종목 목록은 빈 배열입니다.
            토스 401 시 토큰을 재발급하여 한 번만 재조회합니다. 계속 실패하면 422입니다.
            조회 결과를 Position에 저장하거나 주문을 실행하지 않습니다. fetchedAt은 서버 수신 시각입니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "보유 주식 조회 성공. 미보유 시 items=[]"),
            @ApiResponse(responseCode = "400", description = "계좌 연결 ID 또는 종목 코드 형식 오류", content = @Content),
            @ApiResponse(responseCode = "401", description = "서비스 인증 실패 또는 회원 없음", content = @Content),
            @ApiResponse(responseCode = "404", description = "계좌 없음·타 회원 계좌 또는 토스 조회 종목 없음", content = @Content),
            @ApiResponse(responseCode = "409", description = "연결 미완료, 다른 증권사 또는 저장된 인증정보 누락", content = @Content),
            @ApiResponse(responseCode = "422", description = "토스 인증정보·허용 IP·접근 권한 확인 필요", content = @Content),
            @ApiResponse(responseCode = "502", description = "토스 응답 형식 오류", content = @Content),
            @ApiResponse(responseCode = "503", description = "토스·Redis 장애, 요청 한도, 발급 중 또는 인증정보 복호화 불가", content = @Content)
    })
    public ResponseEntity<BrokerHoldingsResponse> getHoldings(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "QUANTFLOW 계좌 연결 ID", example = "1") @PathVariable Long connectionId,
            @Parameter(description = "선택 종목 코드/티커. 생략 시 전체 조회", example = "005930")
            @RequestParam(required = false) String symbol) {
        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "회원 인증이 필요합니다.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.getHoldings(userId, connectionId, symbol));
    }
}
