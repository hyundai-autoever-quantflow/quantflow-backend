package org.example.quantflowbackend.domain.automation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.automation.service.AutomationService;
import org.example.quantflowbackend.domain.automation.service.AutomationService.AutomationResponse;
import org.example.quantflowbackend.domain.trading.service.AutoBuyOrderExecutionService;
import org.example.quantflowbackend.domain.trading.service.AutoBuyOrderExecutionService.OrderResult;
import org.example.quantflowbackend.domain.trading.service.BuyOrderPreviewService;
import org.example.quantflowbackend.domain.trading.service.BuyOrderPreviewService.PreviewResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/broker-connections/{connectionId}/automation")
@SecurityRequirement(name = "bearerAuth")
public class AutomationController {
    private final AutomationService service;
    private final BuyOrderPreviewService buyOrderPreviewService;
    private final AutoBuyOrderExecutionService orders;

    @PostMapping("/enable")
    @Operation(summary = "계좌 자동매수 켜기")
    public ResponseEntity<AutomationResponse> enable(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable("connectionId") Long connectionId,
            @Valid @RequestBody EnableRequest request
    ) {
        return response(service.enable(memberId(jwt), connectionId, request.settingsId().longValueExact()));
    }

    @PostMapping("/stop")
    @Operation(summary = "계좌 자동매수 끄기")
    public ResponseEntity<AutomationResponse> stop(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable("connectionId") Long connectionId
    ) {
        return response(service.stop(memberId(jwt), connectionId));
    }

    @GetMapping
    @Operation(summary = "계좌 자동매수 상태 조회")
    public ResponseEntity<AutomationResponse> get(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable("connectionId") Long connectionId
    ) {
        return response(service.get(memberId(jwt), connectionId));
    }

    @GetMapping("/order-preview")
    @Operation(summary = "오늘 추천 종목의 자동매수 예상 수량 조회",
            description = "조회한 현재가와 현금 매수 가능 금액을 기준으로 수수료 반영 전 예상 수량을 계산합니다.")
    public ResponseEntity<PreviewResponse> preview(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable("connectionId") Long connectionId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(buyOrderPreviewService.preview(memberId(jwt), connectionId));
    }

    @GetMapping("/orders/today")
    @Operation(summary = "오늘의 자동매수 주문 상태 조회")
    public ResponseEntity<OrderResult> todayOrder(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable("connectionId") Long connectionId
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(orders.getToday(memberId(jwt), connectionId));
    }

    @PostMapping("/orders/{orderId}/refresh")
    @Operation(summary = "토스증권 주문 상태 갱신",
            description = "증권사 주문 ID가 확인된 주문의 상태와 누적 체결 수량을 갱신합니다.")
    public ResponseEntity<OrderResult> refreshOrder(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable("connectionId") Long connectionId,
            @PathVariable("orderId") Long orderId
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(orders.refresh(memberId(jwt), connectionId, orderId));
    }

    private ResponseEntity<AutomationResponse> response(AutomationResponse result) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
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

    public record EnableRequest(
            @NotNull @Positive @Digits(integer = 19, fraction = 0) @DecimalMax("9223372036854775807")
            @Schema(type = "integer", format = "int64", example = "1")
            BigDecimal settingsId
    ) {}
}
