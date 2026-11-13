package org.example.quantflowbackend.domain.broker.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.broker.dto.BrokerBuyingPowerResponse;
import org.example.quantflowbackend.domain.broker.dto.BrokerPricesResponse;
import org.example.quantflowbackend.domain.broker.service.BrokerTradingQueryService;
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
@Tag(name = "매수 준비 조회", description = "연결 계좌의 매수 가능 금액 및 종목 현재가 조회. 주문을 실행하지 않습니다.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "연결 ID·통화·종목 형식 오류 또는 토스 조회 조건 거절", content = @Content),
        @ApiResponse(responseCode = "401", description = "서비스 인증 실패 또는 회원 없음", content = @Content),
        @ApiResponse(responseCode = "404", description = "없는 연결·다른 회원의 연결 또는 토스 조회 대상 없음", content = @Content),
        @ApiResponse(responseCode = "409", description = "연결 미완료·다른 증권사·인증정보 누락", content = @Content),
        @ApiResponse(responseCode = "422", description = "토스 인증 실패·허용 IP·접근 권한 확인 필요", content = @Content),
        @ApiResponse(responseCode = "502", description = "토스 응답 필드·숫자·일시 형식 또는 반환 통화·종목 불일치", content = @Content),
        @ApiResponse(responseCode = "503", description = "토스·Redis 장애, 요청 한도, 인증정보 복호화 불가 또는 토큰 발급 중", content = @Content)
})
public class BrokerTradingQueryController {
    private final BrokerTradingQueryService service;

    @GetMapping("/{connectionId}/buying-power")
    @Operation(summary = "내 계좌 매수 가능 금액 조회", description = """
            본인 소유의 CONNECTED 토스 연결 계좌만 조회합니다. connectionId는 내 연결 목록의 ID이며 토스 accountSeq가 아닙니다.
            currency는 KRW 또는 USD를 지정합니다. 현금 기반 금액이며 미수거래는 제외합니다.
            금액은 소수 문자열이며 0도 정상입니다. 서버에서 환율 환산·예산 예약·주문 수량 계산을 수행하지 않습니다.
            토스 401이면 토큰을 갱신해 한 번만 재조회합니다. fetchedAt은 서버 수신 시각입니다.
            """)
    public ResponseEntity<BrokerBuyingPowerResponse> getBuyingPower(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "QUANTFLOW 연결 ID", example = "1") @PathVariable Long connectionId,
            @Parameter(description = "필수 조회 통화: KRW 또는 USD. 영문 대소문자 무관", example = "KRW")
            @RequestParam String currency) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.getBuyingPower(userId(jwt), connectionId, currency));
    }

    @GetMapping("/{connectionId}/prices")
    @Operation(summary = "종목 현재가 조회", description = """
            지정한 본인 소유 CONNECTED 토스 연결의 인증정보로 시세를 조회합니다. 시세 자체는 계좌별 정보가 아닙니다.
            symbols는 쉼표로 구분한 최대 200개 코드입니다. 각 코드는 영문·숫자·점·하이픈으로 1~30자입니다.
            공백 제거·대문자 변환 후 중복을 제거합니다. 요청 항목 수 제한은 중복 제거 전에 적용합니다.
            가격은 거래 통화 기준 소수 문자열입니다. 서버 수신 시각 fetchedAt과 토스 가격 시각 timestamp를 구분하세요.
            timestamp는 null일 수 있습니다. 반환되지 않은 종목을 0으로 만들지 않으며 실시간 체결 가능 가격을 보장하지 않습니다.
            토스 401 시 토큰 갱신 후 한 번만 재조회합니다. 주문 생성이나 DB 종목 갱신은 수행하지 않습니다.
            """)
    public ResponseEntity<BrokerPricesResponse> getPrices(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "인증정보를 사용할 QUANTFLOW 연결 ID", example = "1") @PathVariable Long connectionId,
            @Parameter(description = "필수 종목 목록. 최대 200개", example = "005930,AAPL") @RequestParam String symbols) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.getPrices(userId(jwt), connectionId, symbols));
    }

    private Long userId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "회원 인증이 필요합니다.");
        }
    }
}
