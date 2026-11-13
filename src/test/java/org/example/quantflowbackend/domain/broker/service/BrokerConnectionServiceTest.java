package org.example.quantflowbackend.domain.broker.service;

import org.example.quantflowbackend.domain.broker.client.TossApiClient;
import org.example.quantflowbackend.domain.broker.domain.*;
import org.example.quantflowbackend.domain.broker.dto.*;
import org.example.quantflowbackend.domain.broker.repository.*;
import org.example.quantflowbackend.domain.broker.security.BrokerSecretCipher;
import org.example.quantflowbackend.domain.user.domain.*;
import org.example.quantflowbackend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BrokerConnectionServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final BrokerConnectionRepository connections = mock(BrokerConnectionRepository.class);
    private final BrokerCredentialRepository credentials = mock(BrokerCredentialRepository.class);
    private final BrokerTemporaryStore store = mock(BrokerTemporaryStore.class);
    private final TossTokenService tokens = mock(TossTokenService.class);
    private final TossApiClient client = mock(TossApiClient.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private final BrokerSecretCipher cipher = new BrokerSecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
    private final JsonMapper json = new JsonMapper();
    private final BrokerConnectionService service = new BrokerConnectionService(users, connections, credentials, store, cipher, tokens, client, tx);
    private final String ticket = "00000000-0000-0000-0000-000000000001";
    private final TossConnectRequest request = new TossConnectRequest(ticket, "1");
    private final TossApiClient.Account account = new TossApiClient.Account("12345678901", 1L, "BROKERAGE");

    @BeforeEach
    void setup() {
        when(users.existsById(anyLong())).thenReturn(true);
        when(users.findById(1L)).thenReturn(Optional.of(User.create(SocialProvider.GOOGLE, "google", null, null)));
        when(connections.findByUserMemberIdAndRequestKey(anyLong(), anyString())).thenReturn(Optional.empty());
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(tokens.getAccessToken("id", "secret")).thenReturn("token");
        when(client.getAccounts("token")).thenReturn(List.of(account));
        when(connections.saveAndFlush(any())).thenAnswer(invocation -> {
            BrokerConnection connection = invocation.getArgument(0);
            setId(connection);
            return connection;
        });
    }

    private void prepareTicket(Long owner) {
        String data = json.writeValueAsString(new BrokerConnectionService.TicketData(owner, "id", "secret", List.of(account)));
        when(store.get(anyString())).thenReturn(cipher.encrypt(data));
    }

    @Test
    void anotherMemberCannotUseTicket() {
        prepareTicket(2L);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.connect(1L, "key", request)).getStatusCode().value());
        verifyNoInteractions(client, credentials);
        verify(connections, never()).saveAndFlush(any());
    }

    @Test
    void expiredTicketAndUnlistedAccountCannotCreateConnection() {
        assertThrows(ResponseStatusException.class, () -> service.connect(1L, "key", request));
        prepareTicket(1L);
        assertThrows(ResponseStatusException.class, () -> service.connect(1L, "key", new TossConnectRequest(ticket, "999")));
        verifyNoInteractions(client, credentials);
    }

    @Test
    void accessRemovedAfterVerificationDoesNotPersistCredentials() {
        prepareTicket(1L);
        when(client.getAccounts("token")).thenReturn(List.of());
        assertEquals(422, assertThrows(ResponseStatusException.class, () -> service.connect(1L, "key", request)).getStatusCode().value());
        verifyNoInteractions(credentials);
        verify(connections, never()).saveAndFlush(any());
    }

    @Test
    void successfulConnectionPersistsOnlyEncryptedCredentialsAndMaskedAccount() {
        prepareTicket(1L);
        BrokerConnectionResponse response = service.connect(1L, "key", request);
        assertEquals(BrokerConnectionStatus.CONNECTED, response.status());
        assertEquals("*******8901", response.accountNumberMasked());
        ArgumentCaptor<BrokerCredential> captured = ArgumentCaptor.forClass(BrokerCredential.class);
        verify(credentials).save(captured.capture());
        assertFalse(captured.getValue().getEncryptedPayload().contains("secret"));
        var data = json.readValue(cipher.decrypt(captured.getValue().getEncryptedPayload()), BrokerCredentialData.class);
        assertEquals(1L, data.accountSeq());
        assertEquals("secret", data.clientSecret());
        verify(tx).commit(any());
    }

    @Test
    void replayWorksWithoutTicketOrExternalApiAndDifferentPayloadConflicts() {
        BrokerConnection existing = BrokerConnection.create(User.create(SocialProvider.GOOGLE, "google", null, null),
                BrokerType.TOSS, "fingerprint", "*******8901", "ref", "key",
                cipher.fingerprint("toss-link-request", ticket + ":1"));
        setId(existing);
        when(connections.findByUserMemberIdAndRequestKey(1L, "key")).thenReturn(Optional.of(existing));
        assertEquals("1", service.connect(1L, "key", request).connectionId());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> service.connect(1L, "key", new TossConnectRequest(ticket, "2"))).getStatusCode().value());
        verifyNoInteractions(store, tokens, client, credentials);
    }

    @Test
    void duplicateAccountConstraintRollsBackCredentialInsert() {
        prepareTicket(1L);
        doThrow(new DataIntegrityViolationException("duplicate")).when(connections).saveAndFlush(any());
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.connect(1L, "key", request)).getStatusCode().value());
        verify(tx).rollback(any());
        verify(tx, never()).commit(any());
    }

    private void setId(BrokerConnection connection) {
        try {
            var field = BrokerConnection.class.getDeclaredField("connectionId");
            field.setAccessible(true);
            field.set(connection, 1L);
        } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
    }
}
