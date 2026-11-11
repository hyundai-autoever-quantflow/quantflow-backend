package org.example.quantflowbackend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.quantflowbackend.domain.user.domain.SocialProvider;

public record OAuth2LoginRequest(
        @NotNull SocialProvider provider,
        @NotBlank @Size(max = 16384) String idToken
) {
}
