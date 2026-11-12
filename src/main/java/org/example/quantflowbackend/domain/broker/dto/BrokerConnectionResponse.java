package org.example.quantflowbackend.domain.broker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.quantflowbackend.domain.broker.domain.*;
import java.time.LocalDateTime;

@Schema(description = "연결된 증권사 계좌. 인증정보와 토스 토큰은 반환하지 않습니다.")
public record BrokerConnectionResponse(
        @Schema(example = "1") String connectionId,
        BrokerType broker,
        @Schema(example = "*******8901") String accountNumberMasked,
        BrokerConnectionStatus status,
        LocalDateTime lastVerifiedAt) {
    public static BrokerConnectionResponse from(BrokerConnection connection) {
        return new BrokerConnectionResponse(connection.getConnectionId().toString(), connection.getBroker(),
                connection.getAccountNumberMasked(), connection.getStatus(), connection.getLastVerifiedAt());
    }
}
