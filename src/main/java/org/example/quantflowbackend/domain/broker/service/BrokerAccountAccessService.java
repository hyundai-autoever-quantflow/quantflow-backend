package org.example.quantflowbackend.domain.broker.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.broker.domain.*;
import org.example.quantflowbackend.domain.broker.dto.BrokerCredentialData;
import org.example.quantflowbackend.domain.broker.repository.*;
import org.example.quantflowbackend.domain.broker.security.BrokerSecretCipher;
import org.example.quantflowbackend.domain.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class BrokerAccountAccessService {
    private final UserRepository users;
    private final BrokerConnectionRepository connections;
    private final BrokerCredentialRepository credentials;
    private final BrokerSecretCipher cipher;
    private final JsonMapper json = new JsonMapper();

    @Transactional(readOnly = true)
    public BrokerCredentialData load(Long userId, Long connectionId) {
        if (!users.existsById(userId)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "회원 인증이 필요합니다.");
        // Scope the SQL query to the authenticated owner before reading/decrypting credentials.
        BrokerConnection connection = connections.findByConnectionIdAndUserMemberId(connectionId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "연결된 계좌를 찾을 수 없습니다."));
        if (connection.getBroker() != BrokerType.TOSS) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "현재 토스증권 계좌만 조회할 수 있습니다.");
        if (connection.getStatus() != BrokerConnectionStatus.CONNECTED) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "연결 완료된 계좌만 조회할 수 있습니다. 계좌 연결 상태를 확인해주세요.");
        if (connection.getCredentialRef() == null) throw missingCredentials();
        BrokerCredential credential = credentials.findById(connection.getCredentialRef()).orElseThrow(this::missingCredentials);
        BrokerCredentialData data;
        try {
            data = json.readValue(cipher.decrypt(credential.getEncryptedPayload()), BrokerCredentialData.class);
        } catch (JacksonException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "저장된 증권사 인증정보를 확인할 수 없습니다.");
        }
        if (data == null || data.clientId() == null || data.clientId().isBlank()
                || data.clientSecret() == null || data.clientSecret().isBlank()
                || data.accountSeq() == null || data.accountSeq() <= 0) throw missingCredentials();
        return data;
    }

    private ResponseStatusException missingCredentials() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "저장된 계좌 인증정보가 없습니다. 계좌를 다시 연결해주세요.");
    }
}
