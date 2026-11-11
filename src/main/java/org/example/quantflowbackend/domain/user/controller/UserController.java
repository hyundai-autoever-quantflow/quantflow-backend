package org.example.quantflowbackend.domain.user.controller;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.user.domain.SocialProvider;
import org.example.quantflowbackend.domain.user.domain.User;
import org.example.quantflowbackend.domain.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserRepository userRepository;

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new UserResponse(
                user.getMemberId().toString(), user.getSocialProvider(),
                user.getEmail(), user.getName(), List.copyOf(user.getPreferredCategories())
        );
    }

    public record UserResponse(
            String userId,
            SocialProvider socialProvider,
            String email,
            String name,
            List<String> preferredCategories
    ) {
    }
}
