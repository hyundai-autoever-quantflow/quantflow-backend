package org.example.quantflowbackend.domain.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.refreshtoken.service.RefreshTokenService;
import org.example.quantflowbackend.domain.user.dto.*;
import org.example.quantflowbackend.domain.user.service.UserAuthService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "인증", description = "Android 소셜 로그인, 서비스 토큰 재발급 및 로그아웃 API")
public class UserAuthController {
    private final UserAuthService userAuthService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/oauth2")
    @Operation(summary = "소셜 로그인 및 자동 회원가입", description = """
            Android 앱에서 Google SDK로 발급받은 ID 토큰을 전달합니다. 현재 GOOGLE만 지원합니다.
            서버가 토큰을 검증한 뒤 소셜 제공자와 제공자 사용자 ID로 회원을 조회하며, 최초 로그인은 자동 가입됩니다.
            서비스 Access Token과 Refresh Token은 모두 응답 body로 반환합니다.
            이후 일반 API는 서비스 Access Token을 Authorization: Bearer {accessToken}으로 호출하세요.
            로그인에 Google ID 토큰을 사용하며, 기존 서비스 Access Token은 필요하지 않습니다.
            새 로그인은 해당 회원의 기존 Refresh Token을 대체합니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 또는 최초 가입 성공",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TokenPair.class))),
            @ApiResponse(responseCode = "400", description = "요청 형식 오류 또는 지원하지 않는 소셜 제공자",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(value = "{\"message\":\"지원하지 않는 소셜 로그인 제공자입니다.\"}"))),
            @ApiResponse(responseCode = "401", description = "Google ID 토큰의 서명·발급자·대상·만료 또는 사용자 식별자 검증 실패",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(value = "{\"message\":\"유효하지 않은 Google ID 토큰입니다.\"}"))),
            @ApiResponse(responseCode = "503", description = "Google 공개키 조회 등 인증 인프라 확인 실패",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(value = "{\"message\":\"Google 인증 정보를 확인할 수 없습니다.\"}")))
    })
    public ResponseEntity<TokenPair> loginWithOAuth(@Valid @RequestBody OAuth2LoginRequest request) {
        return tokenResponse(userAuthService.loginWithOAuth(request.provider(), request.idToken()));
    }

    @PostMapping("/reissue")
    @Operation(summary = "토큰 재발급", description = """
            요청 body의 Refresh Token으로 새 Access Token과 Refresh Token을 발급합니다.
            Access Token 없이 호출할 수 있습니다. 성공하면 앱에 저장한 두 토큰을 함께 교체하세요.
            사용한 Refresh Token은 다시 사용할 수 없으며, 동시 재발급은 한 요청만 성공합니다.
            기본 유효기간은 Access Token 15분, Refresh Token 14일이며 서버 설정으로 변경할 수 있습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "새 토큰 쌍 발급 및 기존 Refresh Token 교체 성공",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TokenPair.class))),
            @ApiResponse(responseCode = "400", description = "Refresh Token 누락 또는 요청 형식 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(value = "{\"message\":\"요청 형식이 올바르지 않습니다.\"}"))),
            @ApiResponse(responseCode = "401", description = "Refresh Token 만료·위변조·폐기·재사용 또는 회원 없음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(value = "{\"message\":\"유효하지 않은 리프레시 토큰입니다.\"}")))
    })
    public ResponseEntity<TokenPair> reissue(@Valid @RequestBody RefreshTokenRequest request) {
        return tokenResponse(refreshTokenService.reissue(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = """
            요청 body의 Refresh Token과 일치하는 서버 저장 정보를 폐기합니다. Access Token 없이 호출할 수 있습니다.
            유효한 토큰이라도 이미 폐기되었거나 최신 로그인으로 대체되었다면 최신 토큰을 삭제하지 않고 204를 반환합니다.
            앱에서도 저장한 두 토큰을 제거하세요. 기존 Access Token은 만료까지 유효할 수 있습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 처리 완료, 응답 body 없음", content = @Content),
            @ApiResponse(responseCode = "400", description = "Refresh Token 누락 또는 요청 형식 오류",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(value = "{\"message\":\"요청 형식이 올바르지 않습니다.\"}"))),
            @ApiResponse(responseCode = "401", description = "Refresh Token 자체가 만료·위변조되었거나 토큰 종류가 올바르지 않음",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(type = "object"),
                            examples = @ExampleObject(value = "{\"message\":\"유효하지 않은 리프레시 토큰입니다.\"}")))
    })
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenService.logout(request.refreshToken());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private ResponseEntity<TokenPair> tokenResponse(TokenPair pair) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache").body(pair);
    }
}
