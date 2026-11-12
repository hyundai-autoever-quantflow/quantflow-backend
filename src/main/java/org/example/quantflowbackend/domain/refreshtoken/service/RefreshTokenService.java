package org.example.quantflowbackend.domain.refreshtoken.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.refreshtoken.repository.RedisRefreshTokenRepository;
import org.example.quantflowbackend.domain.user.dto.TokenPair;
import org.example.quantflowbackend.domain.user.repository.UserRepository;
import org.example.quantflowbackend.global.security.JwtTokenProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisRefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public TokenPair reissue(String refreshToken) {
        Long userId = jwtTokenProvider.getUserIdFromRefreshToken(refreshToken);
        if (!userRepository.existsById(userId)) {
            refreshTokenRepository.deleteIfMatches(userId, refreshToken);
            throw invalidToken();
        }
        String access = jwtTokenProvider.createAccessToken(userId);
        String refresh = jwtTokenProvider.createRefreshToken(userId);
        boolean rotated = refreshTokenRepository.rotate(userId, refreshToken, refresh,
                jwtTokenProvider.getRemainingValidity(refresh));
        if (!rotated) {
            // Do not delete the current value: it may belong to a newer login/rotation.
            throw invalidToken();
        }
        return new TokenPair(access, refresh);
    }

    public void logout(String refreshToken) {
        Long userId = jwtTokenProvider.getUserIdFromRefreshToken(refreshToken);
        refreshTokenRepository.deleteIfMatches(userId, refreshToken);
    }

    private ResponseStatusException invalidToken() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다.");
    }
}
