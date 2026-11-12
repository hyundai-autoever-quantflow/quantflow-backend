package org.example.quantflowbackend.global.security.oauth2;

import org.example.quantflowbackend.domain.user.domain.SocialProvider;
import org.example.quantflowbackend.global.security.oauth2.dto.OAuth2UserInfo;

public interface OAuth2TokenVerifier {
    boolean supports(SocialProvider provider);
    OAuth2UserInfo verify(String idToken);
}
