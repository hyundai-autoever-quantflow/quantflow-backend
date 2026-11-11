package org.example.quantflowbackend.domain.user.service;

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
public class TokenIssueService {
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisRefreshTokenRepository refreshTokenRepository;

    public TokenPair issueTokenPair(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "회원을 찾을 수 없습니다.");
        }
        String accessToken = jwtTokenProvider.createAccessToken(userId);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);
        refreshTokenRepository.save(userId, refreshToken,
                jwtTokenProvider.getRemainingValidity(refreshToken));
        return new TokenPair(accessToken, refreshToken);
    }
}
