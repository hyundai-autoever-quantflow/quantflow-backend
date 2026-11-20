package org.example.quantflowbackend.domain.automation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.automation.dto.CreateTradingSettingsRequest;
import org.example.quantflowbackend.domain.automation.service.TradingSettingsService;
import org.example.quantflowbackend.domain.automation.service.TradingSettingsService.SettingsResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/broker-connections/{connectionId}/trading-settings")
@SecurityRequirement(name = "bearerAuth")
public class TradingSettingsController {
    private final TradingSettingsService service;

    @PostMapping
    @Operation(summary = "자동매수 설정 등록",
            description = "사용자의 새 매수 설정을 저장합니다. 자동매수는 계좌의 켜기 API에서 활성화합니다.")
    public ResponseEntity<SettingsResponse> register(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable("connectionId") Long connectionId,
            @Parameter(description = "중복 등록 방지 키. 1~150자", required = true)
            @RequestHeader("Idempotency-Key") String requestKey,
            @Valid @RequestBody CreateTradingSettingsRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(service.create(memberId(jwt), connectionId, requestKey, request));
    }

    @GetMapping("/latest")
    @Operation(summary = "사용자의 최신 자동매수 설정 조회",
            description = "연결된 토스 계좌의 소유권을 확인하고, 로그인한 사용자의 최신 설정을 반환합니다.")
    public ResponseEntity<SettingsResponse> latest(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable("connectionId") Long connectionId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.latest(memberId(jwt), connectionId));
    }

    private Long memberId(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "회원 인증이 필요합니다."
            );
        }

    }
}
