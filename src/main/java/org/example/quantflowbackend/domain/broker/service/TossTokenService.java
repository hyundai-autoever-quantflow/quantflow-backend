package org.example.quantflowbackend.domain.broker.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.broker.client.TossApiClient;
import org.example.quantflowbackend.domain.broker.repository.BrokerTemporaryStore;
import org.example.quantflowbackend.domain.broker.security.BrokerSecretCipher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TossTokenService {
    private final TossApiClient client;
    private final BrokerTemporaryStore store;
    private final BrokerSecretCipher cipher;

    public String getAccessToken(String clientId, String clientSecret) {
        String cacheKey = cacheKey(clientId, clientSecret);
        String cached = store.get(cacheKey);
        if (cached != null) return cipher.decrypt(cached);
        String lockKey = "token-lock:" + cipher.fingerprint("toss-client", clientId);
        String owner = UUID.randomUUID().toString();
        if (!store.lock(lockKey, owner)) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "토스 인증을 처리 중입니다. 잠시 후 다시 시도해주세요.");
        try {
            cached = store.get(cacheKey);
            if (cached != null) return cipher.decrypt(cached);
            long started = System.nanoTime();
            TossApiClient.Token token = client.issueToken(clientId, clientSecret);
            long elapsed = Duration.ofNanos(System.nanoTime() - started).toSeconds();
            long ttl = token.expiresIn() - elapsed - 30;
            if (ttl <= 0 || elapsed >= 55) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "토스 인증 시간이 초과되었습니다. 다시 시도해주세요.");
            store.put(cacheKey, cipher.encrypt(token.accessToken()), Duration.ofSeconds(ttl));
            return token.accessToken();
        } finally {
            store.unlock(lockKey, owner);
        }
    }

    public void invalidate(String clientId, String clientSecret) {
        store.delete(cacheKey(clientId, clientSecret));
    }

    private String cacheKey(String clientId, String clientSecret) {
        return "token:" + cipher.fingerprint("toss-credentials", clientId + "\0" + clientSecret);
    }
}
