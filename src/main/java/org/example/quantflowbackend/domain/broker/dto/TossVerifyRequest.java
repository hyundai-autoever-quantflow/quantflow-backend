package org.example.quantflowbackend.domain.broker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "사용자 개인의 토스 API 인증정보. HTTPS로 전달하며 로그에 기록하지 않습니다.")
public record TossVerifyRequest(
        @NotBlank @Size(max = 255) @Schema(description = "토스 API Client ID") String clientId,
        @NotBlank @Size(max = 1024)
        @Schema(description = "토스 API Client Secret", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        String clientSecret) {
    @Override public String toString() { return "TossVerifyRequest[REDACTED]"; }
}
