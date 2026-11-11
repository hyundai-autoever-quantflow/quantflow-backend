package org.example.quantflowbackend.global.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {
    @Bean
    public SecretKey jwtSigningKey(@Value("${app.jwt.secret-base64}") String encoded) {
        byte[] key = Base64.getDecoder().decode(encoded);
        if (key.length < 32) {
            throw new IllegalArgumentException("JWT 키는 최소 32바이트여야 합니다.");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtSigningKey));
    }

    @Bean
    @Primary
    public JwtDecoder jwtDecoder(
            SecretKey jwtSigningKey,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience
    ) {
        return decoder(jwtSigningKey, issuer, audience, "ACCESS");
    }

    @Bean
    public JwtDecoder refreshTokenDecoder(
            SecretKey jwtSigningKey,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.audience}") String audience
    ) {
        return decoder(jwtSigningKey, issuer, audience, "REFRESH");
    }

    private JwtDecoder decoder(SecretKey key, String issuer, String audience, String type) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                jwt.getAudience() != null && jwt.getAudience().contains(audience)
                        ? OAuth2TokenValidatorResult.success() : invalid();
        OAuth2TokenValidator<Jwt> typeValidator = jwt ->
                type.equals(jwt.getClaimAsString("type")) && jwt.getExpiresAt() != null
                        ? OAuth2TokenValidatorResult.success() : invalid();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator, typeValidator
        ));
        return decoder;
    }

    private OAuth2TokenValidatorResult invalid() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    }
}
