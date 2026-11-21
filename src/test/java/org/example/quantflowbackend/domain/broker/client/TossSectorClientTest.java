package org.example.quantflowbackend.domain.broker.client;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TossSectorClientTest {
    @Test
    void readsSectorHierarchyAndDomesticConstituentsWithoutAccountHeader() {
        var builder = RestClient.builder().baseUrl("https://toss.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        try (var validation = Validation.buildDefaultValidatorFactory()) {
            var client = new TossSectorClient(builder.build(), validation.getValidator());
            server.expect(requestTo("https://toss.test/api/v1/sectors"))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header("Authorization", "Bearer fixture-token"))
                    .andExpect(headerDoesNotExist("X-Tossinvest-Account"))
                    .andRespond(withSuccess("""
                            {"result":{"sectors":[
                              {"sectorId":"10","name":"IT 하드웨어","depth":0,"parentSectorId":null},
                              {"sectorId":"100","name":"반도체","depth":1,"parentSectorId":"10"}
                            ]}}
                            """, MediaType.APPLICATION_JSON));
            server.expect(requestTo("https://toss.test/api/v1/sectors/100/stocks?marketCountry=KR"))
                    .andExpect(headerDoesNotExist("X-Tossinvest-Account"))
                    .andRespond(withSuccess("""
                            {"result":{"stocks":[{"symbol":"0101N0","name":"예시 종목","volume":null,"currency":"KRW"}]}}
                            """, MediaType.APPLICATION_JSON));

            var sectors = client.getSectors("fixture-token");
            assertEquals("10", sectors.get(1).parentSectorId());
            assertEquals(java.util.List.of("0101N0"), client.getDomesticSymbols("fixture-token", "100"));
            server.verify();
        }
    }

    @Test
    void distinguishesAnEmptySectorFromAMalformedResponse() {
        var builder = RestClient.builder().baseUrl("https://toss.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        try (var validation = Validation.buildDefaultValidatorFactory()) {
            var client = new TossSectorClient(builder.build(), validation.getValidator());
            String url = "https://toss.test/api/v1/sectors/100/stocks?marketCountry=KR";
            server.expect(requestTo(url)).andRespond(withSuccess("""
                    {"result":{"stocks":[]}}
                    """, MediaType.APPLICATION_JSON));
            server.expect(requestTo(url)).andRespond(withSuccess("""
                    {"result":{"unexpected":[]}}
                    """, MediaType.APPLICATION_JSON));

            assertTrue(client.getDomesticSymbols("fixture-token", "100").isEmpty());
            var exception = assertThrows(ResponseStatusException.class,
                    () -> client.getDomesticSymbols("fixture-token", "100"));
            assertEquals(502, exception.getStatusCode().value());
            server.verify();
        }
    }
}
