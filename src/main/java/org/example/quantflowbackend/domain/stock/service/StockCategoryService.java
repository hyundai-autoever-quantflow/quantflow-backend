package org.example.quantflowbackend.domain.stock.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.broker.client.TossSectorClient;
import org.example.quantflowbackend.domain.broker.exception.TossAuthenticationException;
import org.example.quantflowbackend.domain.broker.service.TossTokenService;
import org.example.quantflowbackend.domain.stock.repository.StockCategoryRepository;
import org.example.quantflowbackend.domain.stock.repository.StockCategoryRepository.Category;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockCategoryService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Pattern CODE = Pattern.compile(TossSectorClient.SECTOR_CODE_PATTERN);

    private final StockCategoryRepository categories;
    private final TossSectorClient toss;
    private final TossTokenService tokens;
    private final Clock clock;

    @Value("${app.stock-sync.client-id:}")
    private String clientId;

    @Value("${app.stock-sync.client-secret:}")
    private String clientSecret;

    public synchronized List<Category> list() {
        LocalDate today = today();
        var stored = categories.findLatestCatalog();
        if (!stored.isEmpty() && !stored.get(0).listedOn().isBefore(today)) {
            return stored;
        }
        try {
            categories.saveCatalog(read(toss::getSectors), today);
        } catch (ResponseStatusException exception) {
            if (!stored.isEmpty()) {
                return stored;
            }
            throw exception;
        }
        return categories.findLatestCatalog();
    }

    public Category require(String code) {
        validateFormat(code);
        return list().stream().filter(category -> category.code().equals(code))
                .findFirst().orElseThrow(this::invalidCategory);
    }

    public void validateSelection(List<String> codes) {
        if (codes.isEmpty()) {
            return;
        }
        codes.forEach(this::validateFormat);
        var allowed = list().stream().map(Category::code).collect(Collectors.toSet());
        if (!allowed.containsAll(codes)) {
            throw invalidCategory();
        }
    }

    public synchronized void refreshMembership(Category category) {
        var today = today();
        if (categories.lastMembershipSync(category.code()).filter(day -> !day.isBefore(today)).isPresent()) {
            // A stock listing imported after the sector fetch can use the stored membership immediately.
            categories.applyMembership(category.code());
            return;
        }
        var symbols = read(token -> toss.getDomesticSymbols(token, category.code()));
        categories.saveMembership(category.code(), symbols, today);
    }

    private LocalDate today() {
        return clock.instant().atZone(SEOUL).toLocalDate();
    }

    private <T> T read(Function<String, T> request) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "종목 동기화용 토스 인증정보가 설정되지 않았습니다.");
        }
        try {
            return request.apply(tokens.getAccessToken(clientId, clientSecret));
        } catch (TossAuthenticationException exception) {
            tokens.invalidate(clientId, clientSecret);
            return request.apply(tokens.getAccessToken(clientId, clientSecret));
        }
    }

    private void validateFormat(String code) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw invalidCategory();
        }
    }

    private ResponseStatusException invalidCategory() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 카테고리 코드입니다.");
    }
}
