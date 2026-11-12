package org.example.quantflowbackend.domain.broker.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.broker.dto.*;
import org.example.quantflowbackend.domain.broker.service.BrokerConnectionService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/broker-connections")
@RequiredArgsConstructor
@Tag(name = "증권사 계좌", description = "인증된 회원의 실계좌 검증·연결·목록 조회 API")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "요청 형식 오류, 티켓 만료·다른 회원의 티켓·미검증 계좌", content = @Content),
        @ApiResponse(responseCode = "401", description = "서비스 Access Token 오류 또는 회원 없음", content = @Content),
        @ApiResponse(responseCode = "409", description = "계좌 중복 연결, 티켓 재사용 또는 요청 키 충돌", content = @Content),
        @ApiResponse(responseCode = "422", description = "토스 인증정보·허용 IP 오류, 계좌 접근 불가 또는 연결 가능한 계좌 없음", content = @Content),
        @ApiResponse(responseCode = "502", description = "토스 응답 형식 오류", content = @Content),
        @ApiResponse(responseCode = "503", description = "토스·Redis 장애, 인증 처리 중, 요청 한도 또는 암호화 키 미설정", content = @Content)
})
public class BrokerConnectionController {
    private final BrokerConnectionService service;

    @PostMapping("/toss/verify")
    @Operation(summary = "토스 인증정보 검증 및 계좌 목록 조회", description = """
            사용자 개인의 Client ID/Secret으로 토스 API 접근을 검증하고 선택 가능한 종합매매 계좌를 반환합니다.
            티켓은 로그인 회원에게 묶이며 300초 동안 유효합니다. 원본 계좌번호·인증정보·토스 토큰은 반환하지 않습니다.
            토스 허용 IP에는 백엔드 서버의 공인 출발지 IP가 등록되어 있어야 합니다.
            계좌 접근 검증이며 회원과 계좌 명의자의 동일성 검증은 아닙니다. 실제 주문은 발생하지 않습니다.
            """)
    public ResponseEntity<TossVerifyResponse> verify(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TossVerifyRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.verify(userId(jwt), request));
    }

    @PostMapping("/toss")
    @Operation(summary = "선택한 토스 계좌 연결", description = """
            검증 응답의 티켓과 accountSeq로 계좌를 연결합니다. 저장 직전에 토스 계좌 접근을 다시 확인합니다.
            Idempotency-Key는 앱에서 생성한 UUID 등을 사용하세요. 같은 키와 같은 본문 재전송은 기존 연결을 반환하며,
            같은 키로 다른 본문을 보내면 409입니다. 티켓은 하나의 연결에만 사용할 수 있습니다.
            연결은 자동매수를 활성화하지 않습니다. 토스 인증정보는 암호화해 서버에 보관합니다.
            """)
    @ApiResponse(responseCode = "201", description = "계좌 연결 완료 또는 동일 요청 재전송")
    public ResponseEntity<BrokerConnectionResponse> connect(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "중복 실행 방지 키. 1~150자", required = true)
            @RequestHeader("Idempotency-Key") String requestKey,
            @Valid @RequestBody TossConnectRequest request) {
        BrokerConnectionResponse response = service.connect(userId(jwt), requestKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(response);
    }

    @GetMapping
    @Operation(summary = "내 증권사 계좌 연결 목록 조회", description = "현재 회원의 연결만 조회하며 인증정보는 반환하지 않습니다.")
    public ResponseEntity<List<BrokerConnectionResponse>> list(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(userId(jwt)));
    }

    private Long userId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "회원 인증이 필요합니다.");
        }
    }
}
