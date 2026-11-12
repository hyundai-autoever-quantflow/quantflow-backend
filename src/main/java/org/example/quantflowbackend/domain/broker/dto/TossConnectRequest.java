package org.example.quantflowbackend.domain.broker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;

public record TossConnectRequest(
        @NotNull @Pattern(regexp = "[0-9a-fA-F-]{36}")
        @Schema(description = "계좌 검증 응답에서 받은 verificationTicket") String verificationTicket,
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}")
        @Schema(description = "검증된 계좌 목록에서 선택한 식별자", example = "1") String accountSeq) {
    @Override public String toString() { return "TossConnectRequest[REDACTED]"; }
}
