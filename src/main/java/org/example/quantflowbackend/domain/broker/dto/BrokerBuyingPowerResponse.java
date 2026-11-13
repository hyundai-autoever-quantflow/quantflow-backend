package org.example.quantflowbackend.domain.broker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "연결 계좌의 매수 가능 금액 조회 결과")
public record BrokerBuyingPowerResponse(
        @Schema(description = "QUANTFLOW 계좌 연결 ID. 토스 accountSeq와 다릅니다.", example = "1") String connectionId,
        @Schema(description = "서버가 토스 응답을 받은 시각. 주문 시점의 가용 금액을 보장하지 않습니다.") Instant fetchedAt,
        BuyingPower buyingPower
) { }
