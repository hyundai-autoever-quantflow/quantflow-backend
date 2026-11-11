package org.example.quantflowbackend.domain.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.user.domain.SocialProvider;
import org.example.quantflowbackend.domain.user.domain.User;
import org.example.quantflowbackend.domain.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "회원", description = "인증된 회원 정보 조회 API")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserRepository userRepository;

    @GetMapping("/me")
    @Transactional(readOnly = true)
    @Operation(summary = "내 정보 조회", description = """
            서비스 Access Token으로 인증된 회원의 정보를 조회합니다.
            Swagger 상단 Authorize에 로그인 응답의 Access Token 값을 입력하세요.
            Google ID 토큰이나 Refresh Token은 일반 API 인증에 사용할 수 없습니다.
            관심 카테고리를 선택하지 않았다면 preferredCategories는 빈 배열입니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "내 정보 조회 성공",
                    content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Access Token 누락·만료·위변조, 잘못된 토큰 종류 또는 회원 없음",
                    content = @Content)
    })
    public UserResponse me(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new UserResponse(
                user.getMemberId().toString(), user.getSocialProvider(),
                user.getEmail(), user.getName(), List.copyOf(user.getPreferredCategories())
        );
    }

    @Schema(description = "로그인한 회원의 정보")
    public record UserResponse(
            @Schema(description = "회원 식별자. API에서는 문자열로 전달합니다.", example = "1")
            String userId,
            @Schema(description = "가입한 소셜 로그인 제공자", example = "GOOGLE")
            SocialProvider socialProvider,
            @Schema(description = "검증된 소셜 이메일. 제공되지 않으면 null", example = "user@example.com", nullable = true)
            String email,
            @Schema(description = "소셜 표시 이름. 제공되지 않으면 null", example = "퀀트플로우", nullable = true)
            String name,
            @Schema(description = "관심 카테고리 코드 목록. 미선택 시 빈 배열", example = "[]")
            List<String> preferredCategories
    ) {
    }
}
