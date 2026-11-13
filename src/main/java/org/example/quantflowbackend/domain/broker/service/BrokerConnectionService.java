package org.example.quantflowbackend.domain.broker.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.broker.client.TossApiClient;
import org.example.quantflowbackend.domain.broker.domain.*;
import org.example.quantflowbackend.domain.broker.dto.*;
import org.example.quantflowbackend.domain.broker.repository.*;
import org.example.quantflowbackend.domain.broker.security.BrokerSecretCipher;
import org.example.quantflowbackend.domain.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BrokerConnectionService {
    private static final Duration TICKET_TTL = Duration.ofMinutes(5);
    private final UserRepository users;
    private final BrokerConnectionRepository connections;
    private final BrokerCredentialRepository credentials;
    private final BrokerTemporaryStore store;
    private final BrokerSecretCipher cipher;
    private final TossTokenService tokens;
    private final TossApiClient client;
    private final PlatformTransactionManager transactionManager;
    private final JsonMapper json = new JsonMapper();

    public TossVerifyResponse verify(Long userId, TossVerifyRequest request) {
        requireUser(userId);
        List<TossApiClient.Account> accounts = getAccounts(request.clientId(), request.clientSecret());
        if (accounts.isEmpty()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "연결 가능한 토스 종합매매 계좌가 없습니다.");
        String ticket = UUID.randomUUID().toString();
        TicketData data = new TicketData(userId, request.clientId(), request.clientSecret(), accounts);
        store.put(ticketKey(ticket), cipher.encrypt(json.writeValueAsString(data)), TICKET_TTL);
        return new TossVerifyResponse(ticket, TICKET_TTL.toSeconds(), accounts.stream()
                .map(a -> new TossVerifyResponse.Account(a.accountSeq().toString(), mask(a.accountNo()), a.accountType()))
                .toList());
    }

    public BrokerConnectionResponse connect(Long userId, String requestKey, TossConnectRequest request) {
        requireUser(userId);
        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 150) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key는 1~150자여야 합니다.");
        }
        String requestHash = cipher.fingerprint("toss-link-request", request.verificationTicket() + ":" + request.accountSeq());
        BrokerConnection existing = connections.findByUserMemberIdAndRequestKey(userId, requestKey).orElse(null);
        if (existing != null) return replay(existing, requestHash);

        String encrypted = store.get(ticketKey(request.verificationTicket()));
        if (encrypted == null) throw invalidTicket();
        TicketData ticket = json.readValue(cipher.decrypt(encrypted), TicketData.class);
        if (!Objects.equals(ticket.userId(), userId)) throw invalidTicket();
        TossApiClient.Account selected = ticket.accounts().stream()
                .filter(a -> a.accountSeq().toString().equals(request.accountSeq())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "검증 목록에 없는 계좌입니다."));
        // Recheck access immediately before saving; all external I/O is outside the SQL transaction.
        TossApiClient.Account current = getAccounts(ticket.clientId(), ticket.clientSecret()).stream()
                .filter(a -> a.accountSeq().equals(selected.accountSeq()) && a.accountNo().equals(selected.accountNo()))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "선택한 계좌에 접근할 수 없습니다. 다시 검증해주세요."));
        String fingerprint = cipher.fingerprint("broker-account", "TOSS:" + current.accountNo().replace("-", ""));
        String credentialId = UUID.randomUUID().toString();
        String payload = cipher.encrypt(json.writeValueAsString(
                new BrokerCredentialData(ticket.clientId(), ticket.clientSecret(), current.accountSeq())));
        String ticketHash = cipher.fingerprint("toss-used-ticket", request.verificationTicket());
        try {
            return new TransactionTemplate(transactionManager).execute(status -> {
                var user = users.findById(userId).orElseThrow(this::unauthorized);
                credentials.save(BrokerCredential.create(credentialId, payload));
                BrokerConnection connection = BrokerConnection.create(user, BrokerType.TOSS, fingerprint,
                        mask(current.accountNo()), credentialId, requestKey, requestHash);
                connection.completeTossVerification(ticketHash, LocalDateTime.now());
                return BrokerConnectionResponse.from(connections.saveAndFlush(connection));
            });
        } catch (DataIntegrityViolationException exception) {
            // The failed transaction is rolled back before checking a concurrent identical request.
            existing = connections.findByUserMemberIdAndRequestKey(userId, requestKey).orElse(null);
            if (existing != null) return replay(existing, requestHash);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 연결된 계좌이거나 사용된 검증 티켓입니다.");
        }
    }

    public List<BrokerConnectionResponse> list(Long userId) {
        requireUser(userId);
        return connections.findAllByUserMemberIdOrderByConnectionIdDesc(userId).stream()
                .map(BrokerConnectionResponse::from).toList();
    }

    private List<TossApiClient.Account> getAccounts(String clientId, String secret) {
        String token = tokens.getAccessToken(clientId, secret);
        try {
            return client.getAccounts(token);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 422) tokens.invalidate(clientId, secret);
            throw exception;
        }
    }

    private BrokerConnectionResponse replay(BrokerConnection connection, String requestHash) {
        if (!connection.getRequestHash().equals(requestHash)) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "같은 Idempotency-Key를 다른 요청에 사용할 수 없습니다.");
        return BrokerConnectionResponse.from(connection);
    }

    private String ticketKey(String ticket) {
        return "ticket:" + cipher.fingerprint("toss-ticket", ticket);
    }

    private void requireUser(Long userId) {
        if (!users.existsById(userId)) throw unauthorized();
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "회원 인증이 필요합니다.");
    }

    private ResponseStatusException invalidTicket() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 검증 티켓입니다.");
    }

    private String mask(String accountNo) {
        String number = accountNo.replace("-", "");
        int visible = Math.min(4, number.length() - 1);
        return "*".repeat(number.length() - visible) + number.substring(number.length() - visible);
    }

    public record TicketData(Long userId, String clientId, String clientSecret, List<TossApiClient.Account> accounts) {
        @Override public String toString() { return "TicketData[REDACTED]"; }
    }
}
