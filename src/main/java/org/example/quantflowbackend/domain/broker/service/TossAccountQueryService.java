package org.example.quantflowbackend.domain.broker.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.broker.dto.BrokerCredentialData;
import org.example.quantflowbackend.domain.broker.exception.TossAuthenticationException;
import org.springframework.stereotype.Service;

import java.util.function.BiFunction;

/** Common account context for read-only Toss calls. Never use automatic retries for order creation. */
@Service
@RequiredArgsConstructor
public class TossAccountQueryService {
    private final BrokerAccountAccessService access;
    private final TossTokenService tokens;

    public <T> T read(Long userId, Long connectionId, BiFunction<String, Long, T> query) {
        // This service has no DB transaction; the credential loading transaction ends before HTTP I/O.
        BrokerCredentialData data = access.load(userId, connectionId);
        String token = tokens.getAccessToken(data.clientId(), data.clientSecret());
        try {
            return query.apply(token, data.accountSeq());
        } catch (TossAuthenticationException exception) {
            // External reissuance can invalidate a still-cached token. Retry this read only once.
            tokens.invalidate(data.clientId(), data.clientSecret());
            token = tokens.getAccessToken(data.clientId(), data.clientSecret());
            try {
                return query.apply(token, data.accountSeq());
            } catch (TossAuthenticationException retryFailure) {
                tokens.invalidate(data.clientId(), data.clientSecret());
                throw retryFailure;
            }
        }
    }
}
