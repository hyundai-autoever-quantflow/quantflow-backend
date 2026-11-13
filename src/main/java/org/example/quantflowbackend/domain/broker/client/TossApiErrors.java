package org.example.quantflowbackend.domain.broker.client;

import org.example.quantflowbackend.domain.broker.exception.TossAuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

final class TossApiErrors {
    private TossApiErrors() { }

    static ResponseStatusException translate(RestClientResponseException exception, boolean tokenRequest) {
        int code = exception.getStatusCode().value();
        // Do not propagate the upstream response body or exception: it may contain secrets.
        if (code == 401) return new TossAuthenticationException(tokenRequest
                ? "토스 API 인증정보를 확인해주세요."
                : "토스 인증이 만료되었거나 해제되었습니다. 다시 검증해주세요.");
        if (tokenRequest && code == 400) return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "토스 API 인증정보를 확인해주세요.");
        if (code == 403) return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "토스 API의 서버 허용 IP 설정 및 접근 권한을 확인해주세요.");
        if (code == 429) return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "토스 API 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.");
        return unavailable();
    }

    static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "토스 API에 연결할 수 없습니다.");
    }

    static ResponseStatusException invalidResponse() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "토스 API 응답 형식이 올바르지 않습니다.");
    }
}
