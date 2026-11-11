package org.example.quantflowbackend.global.security.oauth2;

import org.example.quantflowbackend.domain.user.domain.SocialProvider;
import org.example.quantflowbackend.global.security.oauth2.dto.OAuth2UserInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class GoogleIdTokenVerifier implements OAuth2TokenVerifier {
    private final JwtDecoder decoder;

    public GoogleIdTokenVerifier(
            @Value("${app.oauth2.google.client-id}") String clientId,
            @Value("${app.oauth2.google.jwk-set-uri:https://www.googleapis.com/oauth2/v3/certs}") String jwkSetUri
    ) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(3));
        NimbusJwtDecoder googleDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(factory))
                .build();
        OAuth2TokenValidator<Jwt> issuer = jwt ->
                ("https://accounts.google.com".equals(jwt.getClaimAsString("iss"))
                        || "accounts.google.com".equals(jwt.getClaimAsString("iss")))
                        ? OAuth2TokenValidatorResult.success()
                        : invalid();
        OAuth2TokenValidator<Jwt> audience = jwt ->
                jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                        ? OAuth2TokenValidatorResult.success()
                        : invalid();
        // Require exp explicitly: timestamp validators otherwise permit a missing exp.
        OAuth2TokenValidator<Jwt> expiry = jwt -> jwt.getExpiresAt() != null
                ? OAuth2TokenValidatorResult.success() : invalid();
        googleDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), issuer, audience, expiry
        ));
        this.decoder = googleDecoder;
    }

    @Override
    public boolean supports(SocialProvider provider) {
        return provider == SocialProvider.GOOGLE;
    }

    @Override
    public OAuth2UserInfo verify(String idToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (BadJwtException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 Google ID 토큰입니다.");
        } catch (JwtException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Google 인증 정보를 확인할 수 없습니다.");
        }
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank() || subject.length() > 255) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google 사용자 식별자가 유효하지 않습니다.");
        }
        String email = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))
                ? jwt.getClaimAsString("email") : null;
        return new OAuth2UserInfo(SocialProvider.GOOGLE, subject, email, jwt.getClaimAsString("name"));
    }

    private static OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    }
}
