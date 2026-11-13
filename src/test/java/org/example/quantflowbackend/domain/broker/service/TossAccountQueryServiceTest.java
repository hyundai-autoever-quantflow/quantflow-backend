package org.example.quantflowbackend.domain.broker.service;

import org.example.quantflowbackend.domain.broker.dto.BrokerCredentialData;
import org.example.quantflowbackend.domain.broker.exception.TossAuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TossAccountQueryServiceTest {
    private final BrokerAccountAccessService access = mock(BrokerAccountAccessService.class);
    private final TossTokenService tokens = mock(TossTokenService.class);
    @SuppressWarnings("unchecked")
    private final BiFunction<String, Long, String> query = mock(BiFunction.class);
    private final TossAccountQueryService service = new TossAccountQueryService(access, tokens);

    @BeforeEach
    void setup() {
        when(access.load(1L, 10L)).thenReturn(new BrokerCredentialData("id", "secret", 42L));
        when(tokens.getAccessToken("id", "secret")).thenReturn("cached", "new");
    }

    @Test
    void usesStoredAccountSequenceAndCachedToken() {
        when(query.apply("cached", 42L)).thenReturn("holdings");
        assertEquals("holdings", service.read(1L, 10L, query));
        verify(query).apply("cached", 42L);
        verify(tokens, never()).invalidate(anyString(), anyString());
    }

    @Test
    void unauthorizedReadRefreshesTokenAndRetriesExactlyOnce() {
        when(query.apply("cached", 42L)).thenThrow(new TossAuthenticationException("expired"));
        when(query.apply("new", 42L)).thenReturn("holdings");
        assertEquals("holdings", service.read(1L, 10L, query));
        verify(tokens).invalidate("id", "secret");
        verify(tokens, times(2)).getAccessToken("id", "secret");
        verify(query).apply("new", 42L);
    }

    @Test
    void repeatedUnauthorizedReadDoesNotLoopAndDropsRejectedCache() {
        when(query.apply(anyString(), eq(42L))).thenThrow(new TossAuthenticationException("expired"));
        assertThrows(TossAuthenticationException.class, () -> service.read(1L, 10L, query));
        verify(query, times(2)).apply(anyString(), eq(42L));
        verify(tokens, times(2)).invalidate("id", "secret");
    }

    @Test
    void forbiddenOrTransientFailureDoesNotTriggerReissuance() {
        when(query.apply("cached", 42L)).thenThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "IP restriction"));
        assertThrows(ResponseStatusException.class, () -> service.read(1L, 10L, query));
        verify(tokens, times(1)).getAccessToken("id", "secret");
        verify(tokens, never()).invalidate(anyString(), anyString());
    }

    @Test
    void invalidClientCredentialsDuringTokenIssueAreNotRetried() {
        when(tokens.getAccessToken("id", "secret")).thenThrow(new TossAuthenticationException("invalid client"));
        assertThrows(TossAuthenticationException.class, () -> service.read(1L, 10L, query));
        verifyNoInteractions(query);
        verify(tokens, times(1)).getAccessToken("id", "secret");
    }
}
