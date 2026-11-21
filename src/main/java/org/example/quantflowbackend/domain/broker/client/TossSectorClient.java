package org.example.quantflowbackend.domain.broker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;

import static org.example.quantflowbackend.domain.broker.client.TossApiErrors.invalidResponse;
import static org.example.quantflowbackend.domain.broker.client.TossApiErrors.translate;
import static org.example.quantflowbackend.domain.broker.client.TossApiErrors.unavailable;

@Component
public class TossSectorClient {
    public static final String SECTOR_CODE_PATTERN = "[A-Za-z0-9_-]{1,40}";

    private final RestClient client;
    private final Validator validator;

    public TossSectorClient(@Qualifier("tossRestClient") RestClient client, Validator validator) {
        this.client = client;
        this.validator = validator;
    }

    public List<Sector> getSectors(String accessToken) {
        var response = get(accessToken, uri -> uri.path("/api/v1/sectors").build(), SectorEnvelope.class);
        var sectors = response.result().sectors();
        var ids = new HashSet<String>();
        if (sectors.stream().anyMatch(sector -> !ids.add(sector.sectorId()))) {
            throw invalidResponse();
        }
        return List.copyOf(sectors);
    }

    public List<String> getDomesticSymbols(String accessToken, String sectorId) {
        var response = get(accessToken, uri -> uri.path("/api/v1/sectors/{sectorId}/stocks")
                .queryParam("marketCountry", "KR").build(sectorId), SectorStocksEnvelope.class);
        return response.result().stocks().stream().map(SectorStock::symbol).distinct().toList();
    }

    private <T> T get(String accessToken, Function<UriBuilder, URI> uri, Class<T> responseType) {
        try {
            var response = client.get().uri(uri)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve().body(responseType);
            if (response == null || !validator.validate(response).isEmpty()) {
                throw invalidResponse();
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw translate(exception, false);
        } catch (ResourceAccessException exception) {
            throw unavailable();
        } catch (RestClientException exception) {
            throw invalidResponse();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Sector(
            @NotNull @Pattern(regexp = SECTOR_CODE_PATTERN) String sectorId,
            @NotBlank @Size(max = 200) String name,
            @NotNull @Min(0) @Max(2) Integer depth,
            @Pattern(regexp = SECTOR_CODE_PATTERN) String parentSectorId
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SectorEnvelope(@NotNull @Valid SectorResult result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SectorResult(@NotEmpty List<@NotNull @Valid Sector> sectors) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SectorStocksEnvelope(@NotNull @Valid SectorStocksResult result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SectorStocksResult(@NotNull List<@NotNull @Valid SectorStock> stocks) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SectorStock(@NotNull @Pattern(regexp = "[A-Z0-9]{6}") String symbol) {}
}
