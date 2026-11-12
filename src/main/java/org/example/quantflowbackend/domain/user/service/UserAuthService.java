package org.example.quantflowbackend.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.user.domain.SocialProvider;
import org.example.quantflowbackend.domain.user.domain.User;
import org.example.quantflowbackend.domain.user.dto.TokenPair;
import org.example.quantflowbackend.domain.user.repository.UserRepository;
import org.example.quantflowbackend.global.security.oauth2.OAuth2TokenVerifier;
import org.example.quantflowbackend.global.security.oauth2.dto.OAuth2UserInfo;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserAuthService {
    private final List<OAuth2TokenVerifier> tokenVerifiers;
    private final UserRepository userRepository;
    private final TokenIssueService tokenIssueService;
    private final PlatformTransactionManager transactionManager;

    public TokenPair loginWithOAuth(SocialProvider provider, String idToken) {
        OAuth2TokenVerifier verifier = tokenVerifiers.stream()
                .filter(candidate -> candidate.supports(provider))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 로그인 제공자입니다."
                ));
        OAuth2UserInfo info = verifier.verify(idToken);
        User user = findOrCreateOAuthUser(info);
        return tokenIssueService.issueTokenPair(user.getMemberId());
    }

    private User findOrCreateOAuthUser(OAuth2UserInfo info) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            return Objects.requireNonNull(transaction.execute(status ->
                    userRepository.findBySocialProviderAndProviderId(
                            info.provider(), info.providerId()
                    ).orElseGet(() -> userRepository.saveAndFlush(User.create(
                            info.provider(), info.providerId(), info.email(), info.name()
                    )))
            ));
        } catch (DataIntegrityViolationException exception) {
            return userRepository.findBySocialProviderAndProviderId(
                    info.provider(), info.providerId()
            ).orElseThrow(() -> exception);
        }
    }
}
