package org.example.quantflowbackend.domain.user.repository;

import jakarta.persistence.LockModeType;
import org.example.quantflowbackend.domain.user.domain.SocialProvider;
import org.example.quantflowbackend.domain.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findBySocialProviderAndProviderId(
            SocialProvider socialProvider,
            String providerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findByMemberId(Long memberId);
}
