package org.example.quantflowbackend.global.security.oauth2.dto;

import org.example.quantflowbackend.domain.user.domain.SocialProvider;

import java.util.Objects;

public record OAuth2UserInfo(
        SocialProvider provider,
        String providerId,
        String email,
        String name
) {
    public OAuth2UserInfo {
        Objects.requireNonNull(provider, "provider는 필수입니다.");
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId는 필수입니다.");
        }
        if (name != null && name.codePointCount(0, name.length()) > 50) {
            name = name.substring(0, name.offsetByCodePoints(0, 50));
        }
    }
}
