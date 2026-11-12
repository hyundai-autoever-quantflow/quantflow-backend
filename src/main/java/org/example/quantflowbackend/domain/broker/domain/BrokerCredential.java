package org.example.quantflowbackend.domain.broker.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.quantflowbackend.global.entity.BaseCreatedTimeEntity;
import java.util.Objects;

@Getter
@Entity
@Table(name = "broker_credentials")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrokerCredential extends BaseCreatedTimeEntity {
    @Id @Column(name = "credential_id", length = 36)
    private String credentialId;

    @Column(name = "encrypted_payload", nullable = false, columnDefinition = "TEXT")
    private String encryptedPayload;

    @Builder
    private BrokerCredential(String credentialId, String encryptedPayload) {
        this.credentialId = Objects.requireNonNull(credentialId);
        this.encryptedPayload = Objects.requireNonNull(encryptedPayload);
    }

    public static BrokerCredential create(String credentialId, String encryptedPayload) {
        return BrokerCredential.builder().credentialId(credentialId).encryptedPayload(encryptedPayload).build();
    }
}
