package org.example.quantflowbackend.domain.broker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Component
public class TossApiClient {
    private final RestClient client;

    public TossApiClient(@Qualifier("tossRestClient") RestClient client) {
        this.client = client;
    }

    public Token issueToken(String clientId, String clientSecret) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        try {
            Token token = client.post().uri("/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(Token.class);
            if (token == null || token.accessToken() == null || token.accessToken().isBlank()
                    || !"Bearer".equalsIgnoreCase(token.tokenType()) || token.expiresIn() <= 30
                    || token.expiresIn() > 31_536_000) throw invalidResponse();
            return token;
        } catch (RestClientResponseException exception) {
            throw translate(exception, true);
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    public List<Account> getAccounts(String accessToken) {
        try {
            Accounts response = client.get().uri("/api/v1/accounts")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve().body(Accounts.class);
            if (response == null || response.result() == null || response.result().stream().anyMatch(
                    a -> a == null || a.accountSeq() == null || a.accountSeq() <= 0
                            || a.accountNo() == null || !a.accountNo().matches("[0-9-]{4,50}")
                            || !a.accountNo().replace("-", "").matches("[0-9]{4,50}")
                            || a.accountType() == null)) throw invalidResponse();
            return response.result().stream().filter(a -> "BROKERAGE".equals(a.accountType())).toList();
        } catch (RestClientResponseException exception) {
            throw translate(exception, false);
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private ResponseStatusException translate(RestClientResponseException exception, boolean tokenRequest) {
        int code = exception.getStatusCode().value();
        // Never propagate the upstream body or exception: these may contain credentials.
        if (code == 401 || (tokenRequest && code == 400)) {
            return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    tokenRequest ? "토스 API 인증정보를 확인해주세요." : "토스 인증이 만료되었거나 해제되었습니다. 다시 검증해주세요.");
        }
        if (code == 403) return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "토스 API의 서버 허용 IP 설정 및 접근 권한을 확인해주세요.");
        if (code == 429) return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "토스 API 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.");
        return unavailable();
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "토스 API에 연결할 수 없습니다.");
    }

    private ResponseStatusException invalidResponse() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "토스 API 응답 형식이 올바르지 않습니다.");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Token(@JsonProperty("access_token") String accessToken,
                        @JsonProperty("token_type") String tokenType,
                        @JsonProperty("expires_in") long expiresIn) {
        @Override public String toString() { return "Token[REDACTED]"; }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(String accountNo, Long accountSeq, String accountType) {
        @Override public String toString() { return "Account[REDACTED]"; }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Accounts(List<Account> result) { }
}
