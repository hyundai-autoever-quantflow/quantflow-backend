package org.example.quantflowbackend.global.config;

import org.example.quantflowbackend.domain.news.client.NaverNewsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NaverNewsConfigTest {
    private MockRestServiceServer server;
    private NaverNewsClient client;

    @BeforeEach
    void setup() {
        var builder = new NaverNewsConfig()
                .naverNewsRestClient("test-id", "test-secret")
                .mutate();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NaverNewsClient(builder.build());
    }

    @Test
    void readsJsonNewsWithPlainTextOrJsonContentType() {
        String payload = """
                {"total":1,"start":1,"display":1,"items":[
                    {"title":"반도체 뉴스","description":"기사 설명"}
                ]}
                """;
        for (MediaType type : new MediaType[]{
                MediaType.parseMediaType("text/plain;charset=UTF-8"),
                MediaType.APPLICATION_JSON
        }) {
            server.expect(anything()).andRespond(withSuccess(payload, type));
        }
        for (int i = 0; i < 2; i++) {
            var response = client.search("반도체", 1, 1, "date");
            assertEquals(1, response.items().size());
            assertEquals("반도체 뉴스", response.items().get(0).title());
        }
        server.verify();
    }

    @Test
    void rejectsNonJsonPlainTextAsBadGateway() {
        server.expect(anything()).andRespond(withSuccess("not JSON", MediaType.TEXT_PLAIN));
        var exception = assertThrows(ResponseStatusException.class,
                () -> client.search("반도체", 1, 1, "date"));
        assertEquals(502, exception.getStatusCode().value());
        server.verify();
    }
}
