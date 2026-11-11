package org.example.quantflowbackend.domain.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.refreshtoken.service.RefreshTokenService;
import org.example.quantflowbackend.domain.user.dto.*;
import org.example.quantflowbackend.domain.user.service.UserAuthService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class UserAuthController {
    private final UserAuthService userAuthService;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/oauth2")
    public ResponseEntity<TokenPair> loginWithOAuth(@Valid @RequestBody OAuth2LoginRequest request) {
        return tokenResponse(userAuthService.loginWithOAuth(request.provider(), request.idToken()));
    }

    @PostMapping("/reissue")
    public ResponseEntity<TokenPair> reissue(@Valid @RequestBody RefreshTokenRequest request) {
        return tokenResponse(refreshTokenService.reissue(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenService.logout(request.refreshToken());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private ResponseEntity<TokenPair> tokenResponse(TokenPair pair) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Pragma", "no-cache").body(pair);
    }
}
