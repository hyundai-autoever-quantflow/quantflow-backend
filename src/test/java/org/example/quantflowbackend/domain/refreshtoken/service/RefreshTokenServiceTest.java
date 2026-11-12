package org.example.quantflowbackend.domain.refreshtoken.service;

import org.example.quantflowbackend.domain.refreshtoken.repository.RedisRefreshTokenRepository;
import org.example.quantflowbackend.domain.user.dto.TokenPair;
import org.example.quantflowbackend.domain.user.repository.UserRepository;
import org.example.quantflowbackend.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {
    private JwtTokenProvider jwtTokenProvider;
    private RedisRefreshTokenRepository refreshTokenRepository;
    private UserRepository userRepository;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = mock(JwtTokenProvider.class);
        refreshTokenRepository = mock(RedisRefreshTokenRepository.class);
        userRepository = mock(UserRepository.class);
        refreshTokenService = new RefreshTokenService(
                jwtTokenProvider, refreshTokenRepository, userRepository
        );
    }

    private void prepareExistingMember() {
        when(jwtTokenProvider.getUserIdFromRefreshToken("old")).thenReturn(1L);
        when(userRepository.existsById(1L)).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(1L)).thenReturn("new-access");
        when(jwtTokenProvider.createRefreshToken(1L)).thenReturn("new-refresh");
        when(jwtTokenProvider.getRemainingValidity("new-refresh")).thenReturn(Duration.ofDays(14));
    }

    @Test
    void successfulRotationReturnsBothNewTokens() {
        prepareExistingMember();
        when(refreshTokenRepository.rotate(1L, "old", "new-refresh", Duration.ofDays(14)))
                .thenReturn(true);

        assertEquals(new TokenPair("new-access", "new-refresh"), refreshTokenService.reissue("old"));
        verify(refreshTokenRepository, never()).save(anyLong(), anyString(), any());
    }

    @Test
    void staleTokenCannotDeleteTheCurrentSession() {
        prepareExistingMember();
        when(refreshTokenRepository.rotate(1L, "old", "new-refresh", Duration.ofDays(14)))
                .thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> refreshTokenService.reissue("old")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(refreshTokenRepository, never()).deleteIfMatches(anyLong(), anyString());
    }

    @Test
    void deletedMemberCannotReceiveNewTokens() {
        when(jwtTokenProvider.getUserIdFromRefreshToken("old")).thenReturn(1L);
        when(userRepository.existsById(1L)).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> refreshTokenService.reissue("old"));

        verify(refreshTokenRepository).deleteIfMatches(1L, "old");
        verify(jwtTokenProvider, never()).createAccessToken(anyLong());
        verify(refreshTokenRepository, never()).rotate(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void logoutOnlyRevokesThePresentedToken() {
        when(jwtTokenProvider.getUserIdFromRefreshToken("old")).thenReturn(1L);

        refreshTokenService.logout("old");

        verify(refreshTokenRepository).deleteIfMatches(1L, "old");
        verifyNoMoreInteractions(refreshTokenRepository);
    }
}
