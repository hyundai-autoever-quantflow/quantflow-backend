package org.example.quantflowbackend.domain.recommendation.repository;

import lombok.RequiredArgsConstructor;
import org.example.quantflowbackend.domain.recommendation.dto.RankingSnapshot;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RankingSnapshotRepository {
    private static final String KEY_PREFIX = "quantflow:ranking:snapshot:";
    private static final Duration RETENTION = Duration.ofDays(30);

    private final StringRedisTemplate redis;
    private final JsonMapper json = new JsonMapper();

    public void save(RankingSnapshot snapshot) {
        try {
            redis.opsForValue().set(
                    KEY_PREFIX + snapshot.rankingDate(),
                    json.writeValueAsString(snapshot),
                    RETENTION
            );
        } catch (DataAccessException | JacksonException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "랭킹 저장에 실패했습니다."
            );
        }
    }

    public Optional<RankingSnapshot> find(LocalDate rankingDate) {
        try {
            String value = redis.opsForValue().get(KEY_PREFIX + rankingDate);
            return value == null
                    ? Optional.empty()
                    : Optional.of(json.readValue(value, RankingSnapshot.class));
        } catch (DataAccessException | JacksonException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "저장된 랭킹을 읽을 수 없습니다."
            );
        }
    }


}
