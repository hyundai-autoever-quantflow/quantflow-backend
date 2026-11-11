package org.example.quantflowbackend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "재발급 또는 로그아웃에 사용하는 Refresh Token 요청")
public record RefreshTokenRequest(
        @Schema(description = "로그인 또는 직전 재발급 응답의 Refresh Token 원문. Access Token을 넣으면 인증에 실패합니다.",
                example = "eyJhbGciOiJIUzI1NiJ9.refresh-payload.signature", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(max = 16384) String refreshToken
) {
}
