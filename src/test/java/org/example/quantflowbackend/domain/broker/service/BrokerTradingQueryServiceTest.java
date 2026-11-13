package org.example.quantflowbackend.domain.broker.service;

import org.example.quantflowbackend.domain.broker.client.TossTradingQueryClient;
import org.example.quantflowbackend.domain.broker.dto.BuyingPower;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BrokerTradingQueryServiceTest {
    private final TossAccountQueryService accounts = mock(TossAccountQueryService.class);
    private final TossTradingQueryClient client = mock(TossTradingQueryClient.class);
    private final BrokerTradingQueryService service = new BrokerTradingQueryService(accounts, client);

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setup() {
        when(accounts.read(anyLong(), anyLong(), any())).thenAnswer(invocation ->
                invocation.getArgument(2, BiFunction.class).apply("token", 42L));
    }

    @Test
    void invalidConnectionIdsAreRejectedBeforeAccountAccess() {
        for (Long id : new Long[]{null, 0L, -1L}) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.getBuyingPower(1L, id, "KRW")).getStatusCode().value());
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.getPrices(1L, id, "AAPL")).getStatusCode().value());
        }
        verify(accounts, never()).read(any(), any(), any());
        verifyNoInteractions(client);
    }

    @Test
    void unsupportedOrMissingCurrencyIsRejectedBeforeAccountAccess() {
        for (String currency : new String[]{null, "", "EUR", "KRW,USD"}) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.getBuyingPower(1L, 10L, currency)).getStatusCode().value());
        }
        verify(accounts, never()).read(any(), any(), any());
    }

    @Test
    void currencyIsNormalizedAndResponseIncludesConnectionAndFetchTime() {
        when(client.getBuyingPower("token", 42L, "USD")).thenReturn(new BuyingPower("USD", new BigDecimal("3500.5")));
        var result = service.getBuyingPower(1L, 10L, " usd ");
        assertEquals("10", result.connectionId());
        assertNotNull(result.fetchedAt());
        assertEquals("USD", result.buyingPower().currency());
        assertEquals("3500.5", result.buyingPower().cashBuyingPower().toPlainString());
    }

    @Test
    void symbolsAreNormalizedDeduplicatedAndPassedToPriceClient() {
        when(client.getPrices("token", List.of("005930", "AAPL", "BRK.B"))).thenReturn(List.of());
        var result = service.getPrices(1L, 10L, "005930, aapl,BRK.B,AAPL");
        verify(client).getPrices("token", List.of("005930", "AAPL", "BRK.B"));
        assertEquals("10", result.connectionId());
        assertNotNull(result.fetchedAt());
        assertTrue(result.prices().isEmpty());
    }

    @Test
    void invalidOrEmptySymbolElementsAreRejected() {
        for (String symbols : new String[]{null, "", " ", "AAPL,", ",AAPL", "AAPL,,MSFT", "/bad", "AAPL?x=1", "A".repeat(31)}) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.getPrices(1L, 10L, symbols)).getStatusCode().value());
        }
        verify(accounts, never()).read(any(), any(), any());
    }

    @Test
    void exactly200SymbolsAreAcceptedBut201AreRejectedBeforeDeduplication() {
        when(client.getPrices(anyString(), anyList())).thenReturn(List.of());
        String max = IntStream.range(0, 200).mapToObj(i -> "S" + i).collect(Collectors.joining(","));
        assertTrue(service.getPrices(1L, 10L, max).prices().isEmpty());
        clearInvocations(accounts, client);
        String tooMany = String.join(",", java.util.Collections.nCopies(201, "AAPL"));
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.getPrices(1L, 10L, tooMany)).getStatusCode().value());
        verify(accounts, never()).read(any(), any(), any());
        verifyNoInteractions(client);
    }
}
