package org.example.quantflowbackend.domain.broker.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.broker.client.TossTradingQueryClient;
import org.example.quantflowbackend.domain.broker.dto.BrokerBuyingPowerResponse;
import org.example.quantflowbackend.domain.broker.dto.BrokerPricesResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BrokerTradingQueryService {
    private final TossAccountQueryService accounts;
    private final TossTradingQueryClient client;

    public BrokerBuyingPowerResponse getBuyingPower(Long userId, Long connectionId, String currency) {
        validateConnectionId(connectionId);
        String normalized = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("KRW") && !normalized.equals("USD")) throw badRequest("통화는 KRW 또는 USD여야 합니다.");
        var result = accounts.read(userId, connectionId,
                (token, accountSeq) -> client.getBuyingPower(token, accountSeq, normalized));
        return new BrokerBuyingPowerResponse(connectionId.toString(), Instant.now(), result);
    }

    public BrokerPricesResponse getPrices(Long userId, Long connectionId, String symbols) {
        validateConnectionId(connectionId);
        List<String> requested = parseSymbols(symbols);
        var result = accounts.read(userId, connectionId, (token, accountSeq) -> client.getPrices(token, requested));
        return new BrokerPricesResponse(connectionId.toString(), Instant.now(), result);
    }

    private List<String> parseSymbols(String symbols) {
        if (symbols == null || symbols.isBlank()) throw badRequest("조회할 종목 코드를 입력해주세요.");
        String[] parts = symbols.split(",", -1);
        if (parts.length > 200) throw badRequest("종목은 한 번에 최대 200개까지 조회할 수 있습니다.");
        List<String> normalized = Arrays.stream(parts).map(String::trim).map(s -> s.toUpperCase(Locale.ROOT)).toList();
        if (normalized.stream().anyMatch(s -> !s.matches("[A-Z0-9.\\-]{1,30}"))) {
            throw badRequest("종목 코드는 영문·숫자·점·하이픈으로 1~30자여야 합니다. 빈 항목은 허용하지 않습니다.");
        }
        return normalized.stream().distinct().toList();
    }

    private void validateConnectionId(Long connectionId) {
        if (connectionId == null || connectionId <= 0) throw badRequest("계좌 연결 ID는 양수여야 합니다.");
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
