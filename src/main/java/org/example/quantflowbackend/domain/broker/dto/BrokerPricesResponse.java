package org.example.quantflowbackend.domain.broker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "종목 현재가 조회 결과. connectionId는 호출 인증정보 선택에 사용되며 시세는 계좌별 정보가 아닙니다.")
public record BrokerPricesResponse(
        @Schema(description = "인증정보 선택에 사용한 QUANTFLOW 계좌 연결 ID", example = "1") String connectionId,
        @Schema(description = "서버 수신 시각. 개별 종목의 가격 시각은 prices[].timestamp입니다.") Instant fetchedAt,
        @Schema(description = "토스가 반환한 시세 목록. 반환되지 않은 가격을 0으로 채우지 않습니다.") List<StockPrice> prices
) { }
