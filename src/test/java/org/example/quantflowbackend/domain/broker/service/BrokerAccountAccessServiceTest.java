package org.example.quantflowbackend.domain.broker.service;

import org.example.quantflowbackend.domain.broker.domain.*;
import org.example.quantflowbackend.domain.broker.repository.*;
import org.example.quantflowbackend.domain.broker.security.BrokerSecretCipher;
import org.example.quantflowbackend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BrokerAccountAccessServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final BrokerConnectionRepository connections = mock(BrokerConnectionRepository.class);
    private final BrokerCredentialRepository credentials = mock(BrokerCredentialRepository.class);
    private final BrokerSecretCipher cipher = spy(new BrokerSecretCipher(Base64.getEncoder().encodeToString(new byte[32])));
    private final BrokerConnection connection = mock(BrokerConnection.class);
    private final BrokerAccountAccessService service = new BrokerAccountAccessService(users, connections, credentials, cipher);

    @BeforeEach
    void setup() {
        when(users.existsById(1L)).thenReturn(true);
        when(connections.findByConnectionIdAndUserMemberId(10L, 1L)).thenReturn(Optional.of(connection));
        when(connection.getBroker()).thenReturn(BrokerType.TOSS);
        when(connection.getStatus()).thenReturn(BrokerConnectionStatus.CONNECTED);
        when(connection.getCredentialRef()).thenReturn("credential-id");
    }

    private void payload(String json) {
        String encrypted = cipher.encrypt(json);
        when(credentials.findById("credential-id")).thenReturn(Optional.of(
                BrokerCredential.create("credential-id", encrypted)));
    }

    @Test
    void loadsOwnedAccountAndDecryptsPersistedCredentialPayload() {
        payload("{\"clientId\":\"id\",\"clientSecret\":\"secret\",\"accountSeq\":42}");
        var result = service.load(1L, 10L);
        assertEquals("id", result.clientId());
        assertEquals("secret", result.clientSecret());
        assertEquals(42L, result.accountSeq());
        assertFalse(result.toString().contains("secret"));
    }

    @Test
    void anotherMembersAccountIsNotFoundBeforeCredentialsAreRead() {
        when(connections.findByConnectionIdAndUserMemberId(10L, 1L)).thenReturn(Optional.empty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.load(1L, 10L)).getStatusCode().value());
        verifyNoInteractions(credentials);
        verify(cipher, never()).decrypt(anyString());
    }

    @Test
    void missingMemberIsUnauthorizedBeforeConnectionLookup() {
        when(users.existsById(1L)).thenReturn(false);
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.load(1L, 10L)).getStatusCode().value());
        verifyNoInteractions(connections, credentials);
    }

    @Test
    void disconnectedAndOtherNonConnectedStatesCannotReadCredentials() {
        for (BrokerConnectionStatus status : BrokerConnectionStatus.values()) {
            if (status == BrokerConnectionStatus.CONNECTED) continue;
            when(connection.getStatus()).thenReturn(status);
            assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.load(1L, 10L)).getStatusCode().value());
        }
        verifyNoInteractions(credentials);
    }

    @Test
    void unsupportedBrokerCannotReadCredentials() {
        when(connection.getBroker()).thenReturn(BrokerType.KIS);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.load(1L, 10L)).getStatusCode().value());
        verifyNoInteractions(credentials);
    }

    @Test
    void missingCredentialRecordOrAccountSequenceRequiresReconnection() {
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.load(1L, 10L)).getStatusCode().value());
        payload("{\"clientId\":\"id\",\"clientSecret\":\"secret\"}");
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.load(1L, 10L)).getStatusCode().value());
    }

    @Test
    void corruptedCiphertextAndMalformedPayloadDoNotExposeSecrets() {
        when(credentials.findById("credential-id")).thenReturn(Optional.of(BrokerCredential.create("credential-id", "corrupt")));
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> service.load(1L, 10L)).getStatusCode().value());
        payload("malformed secret payload");
        var error = assertThrows(ResponseStatusException.class, () -> service.load(1L, 10L));
        assertEquals(503, error.getStatusCode().value());
        assertNull(error.getCause());
        assertFalse(error.toString().contains("secret payload"));
    }
}
