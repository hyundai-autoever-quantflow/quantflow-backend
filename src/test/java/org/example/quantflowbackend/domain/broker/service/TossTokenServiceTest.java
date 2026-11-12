package org.example.quantflowbackend.domain.broker.service;

import org.example.quantflowbackend.domain.broker.client.TossApiClient;
import org.example.quantflowbackend.domain.broker.repository.BrokerTemporaryStore;
import org.example.quantflowbackend.domain.broker.security.BrokerSecretCipher;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TossTokenServiceTest {
    private final TossApiClient client = mock(TossApiClient.class);
    private final BrokerTemporaryStore store = mock(BrokerTemporaryStore.class);
    private final BrokerSecretCipher cipher = new BrokerSecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
    private final TossTokenService service = new TossTokenService(client, store, cipher);

    @Test
    void cachedTokenIsReusedWithoutIssuingAnotherToken() {
        when(store.get(anyString())).thenReturn(cipher.encrypt("cached"));
        assertEquals("cached", service.getAccessToken("id", "secret"));
        verifyNoInteractions(client);
        verify(store, never()).lock(anyString(), anyString());
    }

    @Test
    void concurrentIssuanceIsRejectedWithoutInvalidatingExistingToken() {
        when(store.lock(anyString(), anyString())).thenReturn(false);
        assertThrows(ResponseStatusException.class, () -> service.getAccessToken("id", "secret"));
        verifyNoInteractions(client);
    }

    @Test
    void issuanceStoresCiphertextAndReleasesLock() {
        when(store.lock(anyString(), anyString())).thenReturn(true);
        when(client.issueToken("id", "secret")).thenReturn(new TossApiClient.Token("new-token", "Bearer", 900));
        assertEquals("new-token", service.getAccessToken("id", "secret"));
        verify(store).put(anyString(), argThat(value -> value.startsWith("v1:") && !value.contains("new-token")), any());
        verify(store).unlock(anyString(), anyString());
    }
}
