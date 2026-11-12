package org.example.quantflowbackend.domain.broker.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TossApiClientTest {
    private MockRestServiceServer server;
    private TossApiClient client;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://toss.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TossApiClient(builder.build());
    }

    @Test
    void tokenRequestUsesFormAndStandardOAuthResponse() {
        server.expect(requestTo("https://toss.test/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string("grant_type=client_credentials&client_id=id&client_secret=a%26b"))
                .andRespond(withSuccess("{\"access_token\":\"token\",\"token_type\":\"Bearer\",\"expires_in\":86400}", MediaType.APPLICATION_JSON));
        assertEquals("token", client.issueToken("id", "a&b").accessToken());
        server.verify();
    }

    @Test
    void accountsUseResultEnvelopeAndNoSelectedAccountHeader() {
        server.expect(requestTo("https://toss.test/api/v1/accounts"))
                .andExpect(header("Authorization", "Bearer token"))
                .andExpect(headerDoesNotExist("X-Tossinvest-Account"))
                .andRespond(withSuccess("{\"result\":[{\"accountNo\":\"12345678901\",\"accountSeq\":1,\"accountType\":\"BROKERAGE\"},{\"accountNo\":\"12345678902\",\"accountSeq\":2,\"accountType\":\"FUTURE_TYPE\"}]}", MediaType.APPLICATION_JSON));
        assertEquals(1, client.getAccounts("token").size());
        server.verify();
    }

    @Test
    void upstreamSecretsAreNeverIncludedInPublicError() {
        server.expect(requestTo("https://toss.test/oauth2/token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("client_secret=do-not-leak"));
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> client.issueToken("id", "secret"));
        assertEquals(422, error.getStatusCode().value());
        assertFalse(error.toString().contains("do-not-leak"));
        assertNull(error.getCause());
    }

    @Test
    void malformedAccountEnvelopeFailsClosed() {
        server.expect(requestTo("https://toss.test/api/v1/accounts"))
                .andRespond(withSuccess("{\"result\":null}", MediaType.APPLICATION_JSON));
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> client.getAccounts("token"));
        assertEquals(502, error.getStatusCode().value());
    }
}
