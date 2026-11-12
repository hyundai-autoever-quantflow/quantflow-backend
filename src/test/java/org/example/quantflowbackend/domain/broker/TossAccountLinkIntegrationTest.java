package org.example.quantflowbackend.domain.broker;

import com.sun.net.httpserver.HttpServer;
import org.example.quantflowbackend.domain.broker.repository.BrokerTemporaryStore;
import org.example.quantflowbackend.domain.broker.security.BrokerSecretCipher;
import org.example.quantflowbackend.domain.user.domain.*;
import org.example.quantflowbackend.domain.user.repository.UserRepository;
import org.example.quantflowbackend.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "RUN_TOSS_INTEGRATION_TESTS", matches = "true")
class TossAccountLinkIntegrationTest {
    private static final AtomicInteger tokenRequests = new AtomicInteger();
    private static final AtomicInteger unexpectedRequests = new AtomicInteger();
    private static HttpServer toss;
    @LocalServerPort int port;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired BrokerSecretCipher cipher;
    @Autowired BrokerTemporaryStore store;
    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = new JsonMapper();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        toss = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        toss.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body;
            int status = 200;
            if (path.equals("/oauth2/token") && exchange.getRequestMethod().equals("POST")) {
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                if (!request.contains("grant_type=client_credentials") || !request.contains("client_secret=test-secret")) {
                    status = 401;
                    body = "{\"error\":\"invalid_client\"}";
                } else {
                    tokenRequests.incrementAndGet();
                    body = "{\"access_token\":\"test-toss-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
                }
            } else if (path.equals("/api/v1/accounts") && exchange.getRequestMethod().equals("GET")
                    && "Bearer test-toss-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                body = "{\"result\":[{\"accountNo\":\"12345678901\",\"accountSeq\":1,\"accountType\":\"BROKERAGE\"}]}";
            } else {
                unexpectedRequests.incrementAndGet();
                status = 404;
                body = "{}";
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        toss.start();
        registry.add("app.test-ui.enabled", () -> "true");
        registry.add("app.broker.toss.base-url", () -> "http://127.0.0.1:" + toss.getAddress().getPort());
        registry.add("app.broker.encryption-key-base64", () -> Base64.getEncoder().encodeToString(new byte[32]));
    }

    @Test
    void authenticatedLinkIsIsolatedEncryptedIdempotentAndSafeUnderConcurrency() throws Exception {
        List<Long> memberIds = new ArrayList<>();
        List<String> credentialIds = new ArrayList<>();
        try {
            var ui = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/test-ui/index.html")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, ui.statusCode());
            assertEquals("same-origin-allow-popups", ui.headers().firstValue("Cross-Origin-Opener-Policy").orElse(""));
            assertTrue(ui.body().contains("가입·계좌 연결 테스트"));
            var config = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/test-ui/config")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, config.statusCode());
            assertEquals(1, json.readTree(config.body()).size());
            assertTrue(json.readTree(config.body()).has("googleClientId"));
            var health = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/actuator/health")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, health.statusCode(), health.body());
            assertEquals("UP", json.readTree(health.body()).get("status").asText());
            Long alice = createUser(memberIds);
            Long bob = createUser(memberIds);
            String aliceToken = jwt.createAccessToken(alice);
            String bobToken = jwt.createAccessToken(bob);
            assertEquals(401, post("/toss/verify", null, null, "{}").statusCode());
            assertEquals(400, post("/toss/verify", aliceToken, null, "{}").statusCode());
            var verified = verify(aliceToken);
            String ticket = verified.get("verificationTicket").asText();
            assertEquals("*******8901", verified.get("accounts").get(0).get("accountNumberMasked").asText());
            String request = connectBody(ticket, "1");
            assertEquals(400, post("/toss", bobToken, "foreign-ticket", request).statusCode());
            assertEquals(400, post("/toss", aliceToken, "forged-account", connectBody(ticket, "999")).statusCode());

            // Exercise both the unique request key and the rollback/replay path with simultaneous requests.
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                List<Future<HttpResponse<String>>> futures = executor.invokeAll(List.of(
                        () -> post("/toss", aliceToken, "same-request", request),
                        () -> post("/toss", aliceToken, "same-request", request)));
                var first = futures.get(0).get();
                var second = futures.get(1).get();
                assertEquals(201, first.statusCode(), first.body());
                assertEquals(201, second.statusCode(), second.body());
                assertEquals(json.readTree(first.body()).get("connectionId"), json.readTree(second.body()).get("connectionId"));
                assertFalse(first.body().contains("test-secret"));
                assertFalse(first.body().contains("12345678901"));
            } finally { executor.shutdownNow(); }

            credentialIds.addAll(jdbc.queryForList("SELECT credential_ref FROM broker_connections WHERE user_id = ?", String.class, alice));
            assertEquals(1, credentialIds.size());
            String encrypted = jdbc.queryForObject("SELECT encrypted_payload FROM broker_credentials WHERE credential_id = ?", String.class, credentialIds.get(0));
            assertFalse(encrypted.contains("test-secret"));
            assertEquals(1, json.readTree(cipher.decrypt(encrypted)).get("accountSeq").asInt());
            assertEquals(409, post("/toss", aliceToken, "same-request", connectBody(ticket, "2")).statusCode());
            assertEquals(409, post("/toss", aliceToken, "reused-ticket", request).statusCode());
            String duplicateTicket = verify(aliceToken).get("verificationTicket").asText();
            assertEquals(409, post("/toss", aliceToken, "duplicate-account", connectBody(duplicateTicket, "1")).statusCode());
            String bobTicket = verify(bobToken).get("verificationTicket").asText();
            assertEquals(409, post("/toss", bobToken, "cross-member-account", connectBody(bobTicket, "1")).statusCode());
            assertEquals(1, json.readTree(get(aliceToken, "").body()).size());
            assertEquals(0, json.readTree(get(bobToken, "").body()).size());
            assertEquals(1, tokenRequests.get(), "A live client token should be reused");
            assertEquals(0, unexpectedRequests.get(), "Linking must never place orders");
            store.delete("ticket:" + cipher.fingerprint("toss-ticket", ticket));
            assertEquals(201, post("/toss", aliceToken, "same-request", request).statusCode());
            assertEquals(400, post("/toss", aliceToken, "expired-ticket", request).statusCode());
            var apiDocs = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v3/api-docs")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, apiDocs.statusCode());
            assertTrue(json.readTree(apiDocs.body()).get("paths").has("/api/v1/broker-connections/toss/verify"));
            assertTrue(apiDocs.body().contains("Idempotency-Key"));
            // Failed links cannot leave orphaned durable credentials.
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM broker_credentials", Integer.class));
        } finally {
            for (Long id : memberIds) jdbc.update("DELETE FROM broker_connections WHERE user_id = ?", id);
            for (String id : credentialIds) jdbc.update("DELETE FROM broker_credentials WHERE credential_id = ?", id);
            for (Long id : memberIds) users.deleteById(id);
            toss.stop(0);
        }
    }

    private Long createUser(List<Long> ids) {
        Long id = users.saveAndFlush(User.create(SocialProvider.GOOGLE, "toss-test-" + UUID.randomUUID(), null, "test")).getMemberId();
        ids.add(id);
        return id;
    }
    private JsonNode verify(String token) throws Exception {
        var response = post("/toss/verify", token, null, "{\"clientId\":\"test-client\",\"clientSecret\":\"test-secret\"}");
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));
        return json.readTree(response.body());
    }
    private String connectBody(String ticket, String seq) {
        return json.writeValueAsString(Map.of("verificationTicket", ticket, "accountSeq", seq));
    }
    private HttpResponse<String> post(String path, String token, String key, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/broker-connections" + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (key != null) request.header("Idempotency-Key", key);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(String token, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/broker-connections" + path))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
