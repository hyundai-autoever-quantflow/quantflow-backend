package org.example.quantflowbackend.global.security;

import org.example.quantflowbackend.global.config.JwtConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig({JwtConfig.class, JwtTokenProvider.class})
@TestPropertySource(properties = {
        "app.jwt.secret-base64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.jwt.issuer=quantflow-test",
        "app.jwt.audience=quantflow-test-api",
        "app.jwt.access-token-ttl-seconds=900",
        "app.jwt.refresh-token-ttl-seconds=1209600"
})
class JwtTokenProviderTest {
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    @Qualifier("jwtDecoder")
    private JwtDecoder accessDecoder;

    @Autowired
    @Qualifier("refreshTokenDecoder")
    private JwtDecoder refreshDecoder;

    @Test
    void accessAndRefreshTokensCannotBeUsedInterchangeably() {
        String access = jwtTokenProvider.createAccessToken(1L);
        String refresh = jwtTokenProvider.createRefreshToken(1L);

        assertEquals("1", accessDecoder.decode(access).getSubject());
        assertEquals("REFRESH", refreshDecoder.decode(refresh).getClaimAsString("type"));
        assertThrows(JwtException.class, () -> accessDecoder.decode(refresh));
        assertThrows(JwtException.class, () -> refreshDecoder.decode(access));
    }

    @Test
    void refreshTokensHaveIndependentIdsAndConfiguredLifetime() {
        String first = jwtTokenProvider.createRefreshToken(7L);
        String second = jwtTokenProvider.createRefreshToken(7L);

        assertNotEquals(first, second);
        assertEquals(7L, jwtTokenProvider.getUserIdFromRefreshToken(first));
        Duration remaining = jwtTokenProvider.getRemainingValidity(first);
        assertTrue(remaining.compareTo(Duration.ofDays(14)) <= 0);
        assertTrue(remaining.compareTo(Duration.ofDays(14).minusSeconds(10)) > 0);
    }
}
