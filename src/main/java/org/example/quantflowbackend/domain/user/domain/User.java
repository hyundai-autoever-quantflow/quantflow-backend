package org.example.quantflowbackend.domain.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.quantflowbackend.global.entity.BaseTimeEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider")
    private SocialProvider socialProvider;

    @Column(name = "provider_id")
    private String providerId;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(unique = true, length = 50)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_categories",
            nullable = false,
            columnDefinition = "jsonb")
    private List<String> preferredCategories = new ArrayList<>();

    @Version
    @Column(name = "preferences_version", nullable = false)
    private Integer preferencesVersion;

    @Builder
    private User(
            SocialProvider socialProvider,
            String providerId,
            String email,
            String name
    ) {
        this.socialProvider = socialProvider;
        this.providerId = providerId;
        this.email = email;
        this.name = name;
    }

    public static User create(
            SocialProvider socialProvider,
            String providerId,
            String email,
            String name
    ) {
        return User.builder()
                .socialProvider(socialProvider)
                .providerId(providerId)
                .email(email)
                .name(name)
                .build();
    }

    public void changePreferredCategories(List<String> categoryCodes) {
        Objects.requireNonNull(categoryCodes, "카테고리 목록은 null일 수 없습니다.");

        this.preferredCategories = new ArrayList<>(
                categoryCodes.stream().distinct().toList()
        );
    }
}
