package org.example.quantflowbackend.domain.broker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

import static org.example.quantflowbackend.domain.broker.client.TossApiErrors.*;

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
