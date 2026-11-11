package org.example.quantflowbackend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Android 앱에 응답 body로 전달하는 서비스 토큰 쌍. 재발급 성공 시 두 토큰을 함께 교체합니다.")
public record TokenPair(
        @Schema(description = "일반 API 호출용 서비스 Access Token. Authorization: Bearer {accessToken}으로 전달하며 기본 유효기간은 15분입니다.",
                example = "eyJhbGciOiJIUzI1NiJ9.access-payload.signature", accessMode = Schema.AccessMode.READ_ONLY)
        String accessToken,
        @Schema(description = "재발급·로그아웃 요청 body에 전달하는 Refresh Token. 기본 유효기간은 14일이며 재발급 시 기존 토큰은 폐기됩니다.",
                example = "eyJhbGciOiJIUzI1NiJ9.refresh-payload.signature", accessMode = Schema.AccessMode.READ_ONLY)
        String refreshToken
) {
}
