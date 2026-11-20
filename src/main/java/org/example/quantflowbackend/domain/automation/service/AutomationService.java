package org.example.quantflowbackend.domain.automation.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.automation.domain.Automation;
import org.example.quantflowbackend.domain.automation.domain.AutomationDesiredState;
import org.example.quantflowbackend.domain.automation.domain.AutomationRuntimeState;
import org.example.quantflowbackend.domain.automation.domain.TradingSettingsStatus;
import org.example.quantflowbackend.domain.automation.repository.AutomationRepository;
import org.example.quantflowbackend.domain.automation.repository.TradingSettingsRepository;
import org.example.quantflowbackend.domain.broker.domain.BrokerConnection;
import org.example.quantflowbackend.domain.broker.domain.BrokerConnectionStatus;
import org.example.quantflowbackend.domain.broker.domain.BrokerType;
import org.example.quantflowbackend.domain.broker.repository.BrokerConnectionRepository;
import org.example.quantflowbackend.domain.user.domain.User;
import org.example.quantflowbackend.domain.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AutomationService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final AutomationRepository automations;
    private final TradingSettingsRepository settings;
    private final BrokerConnectionRepository connections;
    private final UserRepository users;

    @Transactional
    public AutomationResponse enable(Long memberId, Long connectionId, Long settingsId) {
        User user = lockUser(memberId);
        var connection = findOwnedConnection(memberId, connectionId);

        if (connection.getBroker() != BrokerType.TOSS
                || connection.getStatus() != BrokerConnectionStatus.CONNECTED) {
            throw conflict("연결 완료된 토스 계좌가 필요합니다.");
        }

        var setting = settings.findBySettingsIdAndUserMemberId(settingsId, memberId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "매수 설정을 찾을 수 없습니다."
                ));

        LocalDateTime now = LocalDateTime.now(SEOUL);
        if (setting.getEffectiveTradingDate().isAfter(now.toLocalDate())) {
            throw conflict("매수 설정은 적용 거래일부터 활성화할 수 있습니다.");
        }
        if (setting.getStatus() == TradingSettingsStatus.SUPERSEDED) {
            throw conflict("교체된 매수 설정은 활성화할 수 없습니다.");
        }

        var automation = automations
                .findByConnectionConnectionIdAndUserMemberId(connectionId, memberId)
                .orElseGet(() -> Automation.create(user, connection, setting));

        if (automation.isKillSwitchActive()) {
            throw conflict("긴급 중지가 활성화돼 있습니다.");
        }

        setting.confirm(now);
        automation.enable(setting);

        return AutomationResponse.from(connectionId, automations.save(automation));
    }

    @Transactional
    public AutomationResponse stop(Long memberId, Long connectionId) {
        lockUser(memberId);
        findOwnedConnection(memberId, connectionId);

        return automations.findByConnectionConnectionIdAndUserMemberId(connectionId, memberId)
                .map(automation -> {
                    automation.stop();
                    return AutomationResponse.from(connectionId, automation);
                })
                .orElseGet(() -> AutomationResponse.off(connectionId));
    }

    @Transactional(readOnly = true)
    public AutomationResponse get(Long memberId, Long connectionId) {
        findOwnedConnection(memberId, connectionId);

        return automations.findByConnectionConnectionIdAndUserMemberId(connectionId, memberId)
                .map(automation -> AutomationResponse.from(connectionId, automation))
                .orElseGet(() -> AutomationResponse.off(connectionId));
    }

    private User lockUser(Long memberId) {
        return users.findByMemberId(memberId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "회원 인증이 필요합니다."
                ));
    }

    private BrokerConnection findOwnedConnection(Long memberId, Long connectionId) {
        return connections.findByConnectionIdAndUserMemberId(connectionId, memberId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "연결된 계좌를 찾을 수 없습니다."
                ));
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record AutomationResponse(
            Long connectionId,
            Long settingsId,
            AutomationDesiredState desiredState,
            AutomationRuntimeState runtimeState,
            boolean killSwitchActive,
            TradingSettingsService.SettingsResponse settings
    ) {
        static AutomationResponse from(Long connectionId, Automation automation) {
            var settings = automation.getSettings();
            return new AutomationResponse(
                    connectionId, settings.getSettingsId(),
                    automation.getDesiredState(), automation.getRuntimeState(),
                    automation.isKillSwitchActive(), TradingSettingsService.SettingsResponse.from(settings)
            );
        }

        static AutomationResponse off(Long connectionId) {
            return new AutomationResponse(
                    connectionId, null, AutomationDesiredState.STOPPED,
                    AutomationRuntimeState.IDLE, false, null
            );
        }
    }
}
