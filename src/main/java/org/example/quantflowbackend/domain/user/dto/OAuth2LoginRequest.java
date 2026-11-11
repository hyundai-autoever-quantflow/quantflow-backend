package org.example.quantflowbackend.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.quantflowbackend.domain.user.domain.SocialProvider;

@Schema(description = "Android 앱에서 Google 로그인 후 전달하는 소셜 인증 요청")
public record OAuth2LoginRequest(
        @Schema(description = "소셜 로그인 제공자. 현재 GOOGLE만 지원합니다.",
                implementation = String.class, allowableValues = {"GOOGLE"}, example = "GOOGLE")
        @NotNull SocialProvider provider,
        @Schema(description = "Google SDK에서 받은 ID 토큰 원문. 서비스 Access Token과 다릅니다. 예시는 실제 토큰으로 교체하세요.",
                example = "eyJhbGciOiJSUzI1NiJ9.payload.signature", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(max = 16384) String idToken
) {
}
