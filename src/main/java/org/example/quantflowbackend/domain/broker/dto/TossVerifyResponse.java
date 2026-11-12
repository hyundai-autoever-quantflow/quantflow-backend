package org.example.quantflowbackend.domain.broker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "계좌 선택에 사용하는 5분 유효 검증 결과")
public record TossVerifyResponse(
        @Schema(description = "현재 회원에게만 유효한 계좌 연결 티켓") String verificationTicket,
        @Schema(description = "티켓 유효기간(초)", example = "300") long expiresIn,
        List<Account> accounts) {
    public record Account(
            @Schema(description = "선택할 토스 계좌 식별자. 문자열로 전달", example = "1") String accountSeq,
            @Schema(description = "마스킹 계좌번호", example = "*******8901") String accountNumberMasked,
            @Schema(example = "BROKERAGE") String accountType) { }
}
