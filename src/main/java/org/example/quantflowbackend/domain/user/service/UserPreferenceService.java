package org.example.quantflowbackend.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.stock.service.StockCategoryService;
import org.example.quantflowbackend.domain.user.domain.User;
import org.example.quantflowbackend.domain.user.repository.UserRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {
    private final UserRepository users;
    private final StockCategoryService categories;
    private final PlatformTransactionManager transactionManager;

    @Transactional(readOnly = true)
    public Preferences get(Long memberId) {
        return Preferences.from(findUser(memberId));
    }

    public Preferences replace(Long memberId, Integer version, List<String> codes) {
        categories.validateSelection(codes);
        try {
            return new TransactionTemplate(transactionManager).execute(status -> {
                var user = findUser(memberId);
                if (!version.equals(user.getPreferencesVersion())) {
                    throw versionConflict();
                }
                user.changePreferredCategories(codes);
                return Preferences.from(users.saveAndFlush(user));
            });
        } catch (OptimisticLockingFailureException exception) {
            throw versionConflict();
        }
    }

    private User findUser(Long memberId) {
        return users.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "회원 인증이 필요합니다."));
    }

    private ResponseStatusException versionConflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "관심 카테고리를 다시 조회한 뒤 변경해주세요.");
    }

    public record Preferences(List<String> preferredCategories, Integer preferencesVersion) {
        static Preferences from(User user) {
            return new Preferences(List.copyOf(user.getPreferredCategories()), user.getPreferencesVersion());
        }
    }
}
