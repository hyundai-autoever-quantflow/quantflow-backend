package org.example.quantflowbackend.domain.trading.service;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.recommendation.service.RankingStockCandidateService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DomesticMarketSessionService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final RankingStockCandidateService market;

    public Optional<RegularMarketSession> findRegularSession(LocalDate date) {
        var day = market.marketCalendar(date).today();
        if (day == null || !date.equals(day.date())) {
            throw marketUnavailable();
        }
        if (day.integrated() == null || day.integrated().regularMarket() == null) {
            return Optional.empty();
        }
        var session = day.integrated().regularMarket();
        if (session.startTime() == null || session.endTime() == null
                || !session.startTime().isBefore(session.endTime())
                || !date.equals(session.startTime().atZoneSameInstant(SEOUL).toLocalDate())
                || !date.equals(session.endTime().atZoneSameInstant(SEOUL).toLocalDate())) {
            throw marketUnavailable();
        }
        return Optional.of(new RegularMarketSession(session.startTime().toInstant(), session.endTime().toInstant()));
    }

    private ResponseStatusException marketUnavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "정규장 운영 시간을 확인할 수 없습니다.");
    }

    public record RegularMarketSession(Instant startsAt, Instant endsAt) {
        public boolean isOpenAt(Instant now) {
            return !now.isBefore(startsAt) && now.isBefore(endsAt);
        }
    }
}
