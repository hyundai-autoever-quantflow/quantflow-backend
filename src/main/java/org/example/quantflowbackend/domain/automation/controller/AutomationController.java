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
