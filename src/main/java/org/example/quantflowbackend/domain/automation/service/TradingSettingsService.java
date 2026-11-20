package org.example.quantflowbackend.domain.automation.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.automation.domain.TradingSettings;
import org.example.quantflowbackend.domain.automation.domain.TradingSettingsStatus;
import org.example.quantflowbackend.domain.automation.dto.CreateTradingSettingsRequest;
import org.example.quantflowbackend.domain.automation.repository.TradingSettingsRepository;
import org.example.quantflowbackend.domain.broker.domain.BrokerConnectionStatus;
import org.example.quantflowbackend.domain.broker.domain.BrokerType;
import org.example.quantflowbackend.domain.broker.repository.BrokerConnectionRepository;
import org.example.quantflowbackend.domain.recommendation.service.RankingStockCandidateService;
import org.example.quantflowbackend.domain.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class TradingSettingsService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final TradingSettingsRepository settingsRepository;
    private final BrokerConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final RankingStockCandidateService marketService;
    private final PlatformTransactionManager transactionManager;

    public SettingsResponse create(
            Long memberId,
            Long connectionId,
            String requestKey,
            CreateTradingSettingsRequest request
    ) {
        if (requestKey == null || requestKey.isBlank() || requestKey.length() > 150) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Idempotency-Key는 1~150자여야 합니다."
            );
        }

        requireConnectedTossAccount(memberId, connectionId);
        String requestHash = hash(connectionId, request);

        var existing = settingsRepository.findByUserMemberIdAndRequestKey(memberId, requestKey);
        if (existing.isPresent()) {
            return replay(existing.get(), requestHash);
        }

        LocalDate effectiveDate = resolveEffectiveDate();

        return new TransactionTemplate(transactionManager).execute(transaction -> {
            var user = userRepository.findByMemberId(memberId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED, "회원 인증이 필요합니다."
                    ));

            requireConnectedTossAccount(memberId, connectionId);

            var concurrent = settingsRepository
                    .findByUserMemberIdAndRequestKey(memberId, requestKey);
            if (concurrent.isPresent()) {
                return replay(concurrent.get(), requestHash);
            }

            int nextVersion = settingsRepository.findFirstByUserMemberIdOrderByVersionDesc(memberId)
                    .map(TradingSettings::getVersion)
                    .orElse(0) + 1;

            var settings = TradingSettings.createForAutoBuy(
                    user, nextVersion, effectiveDate,
                    request.entryBudgetKrw(), request.maxBuysPerDay().shortValueExact(),
                    request.dailyLossLimitKrw(), requestKey, requestHash
            );

            return SettingsResponse.from(settingsRepository.save(settings));
        });
    }

    public SettingsResponse latest(Long memberId, Long connectionId) {
        requireConnectedTossAccount(memberId, connectionId);
        return settingsRepository.findFirstByUserMemberIdOrderByVersionDesc(memberId)
                .map(SettingsResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "등록한 매수 설정이 없습니다."
                ));
    }

    private void requireConnectedTossAccount(Long memberId, Long connectionId) {
        var connection = connectionRepository
                .findByConnectionIdAndUserMemberId(connectionId, memberId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "연결된 계좌를 찾을 수 없습니다."
                ));

        if (connection.getBroker() != BrokerType.TOSS
                || connection.getStatus() != BrokerConnectionStatus.CONNECTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "연결 완료된 토스 계좌가 필요합니다."
            );
        }
    }

    private LocalDate resolveEffectiveDate() {
        LocalDate today = LocalDate.now(SEOUL);
        var calendar = marketService.marketCalendar(today);

        if (!today.equals(calendar.today().date())) {
            throw calendarUnavailable();
        }
        if (calendar.today().integrated() != null) {
            return today;
        }

        var next = calendar.nextBusinessDay();
        if (next == null || next.date() == null
                || !next.date().isAfter(today) || next.integrated() == null) {
            throw calendarUnavailable();
        }
        return next.date();
    }

    private ResponseStatusException calendarUnavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "설정 적용 거래일을 확인할 수 없습니다."
        );
    }

    private SettingsResponse replay(TradingSettings existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "같은 Idempotency-Key를 다른 요청에 사용할 수 없습니다."
            );
        }
        return SettingsResponse.from(existing);
    }

    private String hash(Long connectionId, CreateTradingSettingsRequest request) {
        String input = String.join("|",
                connectionId.toString(),
                request.entryBudgetKrw().stripTrailingZeros().toPlainString(),
                request.maxBuysPerDay().stripTrailingZeros().toPlainString(),
                request.dailyLossLimitKrw().stripTrailingZeros().toPlainString()
        );

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("요청 해시를 생성할 수 없습니다.", exception);
        }
    }

    public record SettingsResponse(
            Long settingsId,
            Integer version,
            TradingSettingsStatus status,
            LocalDate effectiveTradingDate,
            BigDecimal entryBudgetKrw,
            Short maxBuysPerDay,
            BigDecimal dailyLossLimitKrw
    ) {
        static SettingsResponse from(TradingSettings settings) {
            return new SettingsResponse(
                    settings.getSettingsId(),
                    settings.getVersion(),
                    settings.getStatus(),
                    settings.getEffectiveTradingDate(),
                    settings.getEntryBudgetKrw(),
                    settings.getMaxBuysPerDay(),
                    settings.getDailyLossLimitKrw()
            );
        }
    }
}
