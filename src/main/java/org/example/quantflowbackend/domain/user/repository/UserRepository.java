package org.example.quantflowbackend.domain.user.repository;

import org.example.quantflowbackend.domain.user.domain.SocialProvider;
import org.example.quantflowbackend.domain.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findBySocialProviderAndProviderId(
            SocialProvider socialProvider,
            String providerId
    );
}
