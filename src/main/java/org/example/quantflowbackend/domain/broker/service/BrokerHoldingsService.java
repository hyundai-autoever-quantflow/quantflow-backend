package org.example.quantflowbackend.domain.broker.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.broker.client.TossHoldingsClient;
import org.example.quantflowbackend.domain.broker.dto.BrokerHoldingsResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class BrokerHoldingsService {
    private final TossAccountQueryService accounts;
    private final TossHoldingsClient client;

    public BrokerHoldingsResponse getHoldings(Long userId, Long connectionId, String symbol) {
        if (connectionId == null || connectionId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "계좌 연결 ID는 양수여야 합니다.");
        if (symbol != null && !symbol.matches("[A-Za-z0-9.\\-]{1,30}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "종목 코드는 영문·숫자·점·하이픈으로 1~30자여야 합니다.");
        }
        var holdings = accounts.read(userId, connectionId, (token, accountSeq) -> client.getHoldings(token, accountSeq, symbol));
        return new BrokerHoldingsResponse(connectionId.toString(), Instant.now(), holdings);
    }
}
