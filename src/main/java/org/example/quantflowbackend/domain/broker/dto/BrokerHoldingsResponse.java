package org.example.quantflowbackend.domain.broker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "연결 계좌의 토스 보유 주식 조회 결과")
public record BrokerHoldingsResponse(
        @Schema(description = "QUANTFLOW 계좌 연결 ID. 토스 accountSeq와 다릅니다.", example = "1") String connectionId,
        @Schema(description = "백엔드가 토스 응답을 수신한 시각. 시세 기준 시각은 아닙니다.") Instant fetchedAt,
        HoldingsOverview holdings
) { }
