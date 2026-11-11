package org.example.quantflowbackend.global.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider {
    private final JwtEncoder encoder;
    private final JwtDecoder refreshDecoder;
    private final String issuer;
    private final String audience;
    private final long accessTtl;
    private final long refreshTtl;

    public JwtTokenProvider(
            JwtEncoder encoder,
            @Qualifier("refreshTokenDecoder") JwtDecoder refreshDecoder,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience,
            @Value("${app.jwt.access-token-ttl-seconds:900}") long accessTtl,
            @Value("${app.jwt.refresh-token-ttl-seconds:1209600}") long refreshTtl
    ) {
        if (accessTtl <= 0 || refreshTtl <= accessTtl) {
            throw new IllegalArgumentException("리프레시 토큰 유효시간은 액세스 토큰보다 길어야 합니다.");
        }
        this.encoder = encoder;
        this.refreshDecoder = refreshDecoder;
        this.issuer = issuer;
        this.audience = audience;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    public String createAccessToken(Long userId) {
        return create(userId, "ACCESS", accessTtl);
    }

    public String createRefreshToken(Long userId) {
        return create(userId, "REFRESH", refreshTtl);
    }

    public Long getUserIdFromRefreshToken(String token) {
        try {
            return Long.valueOf(refreshDecoder.decode(token).getSubject());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다.");
        }
    }

    public Duration getRemainingValidity(String refreshToken) {
        Instant expiration = refreshDecoder.decode(refreshToken).getExpiresAt();
        if (expiration == null) throw new IllegalArgumentException("토큰 만료 시각이 없습니다.");
        Duration remaining = Duration.between(Instant.now(), expiration);
        if (remaining.isNegative() || remaining.isZero()) {
            throw new IllegalArgumentException("이미 만료된 토큰입니다.");
        }
        return remaining;
    }

    private String create(Long userId, String type, long ttl) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).subject(userId.toString()).audience(List.of(audience))
                .issuedAt(now).expiresAt(now.plusSeconds(ttl))
                .id(UUID.randomUUID().toString()).claim("type", type).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims
        )).getTokenValue();
    }
}
