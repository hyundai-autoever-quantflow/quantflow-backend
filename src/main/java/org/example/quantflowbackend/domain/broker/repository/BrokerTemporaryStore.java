package org.example.quantflowbackend.domain.broker.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class BrokerTemporaryStore {
    private final StringRedisTemplate redis;
    private static final String PREFIX = "quantflow:broker:";
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
            return 0
            """, Long.class);

    public String get(String key) {
        try { return redis.opsForValue().get(PREFIX + key); }
        catch (DataAccessException exception) { throw unavailable(); }
    }

    public void put(String key, String encrypted, Duration ttl) {
        try { redis.opsForValue().set(PREFIX + key, encrypted, ttl); }
        catch (DataAccessException exception) { throw unavailable(); }
    }

    public void delete(String key) {
        try { redis.delete(PREFIX + key); }
        catch (DataAccessException exception) { throw unavailable(); }
    }

    public boolean lock(String key, String owner) {
        try { return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(PREFIX + key, owner, Duration.ofSeconds(60))); }
        catch (DataAccessException exception) { throw unavailable(); }
    }

    public void unlock(String key, String owner) {
        try { redis.execute(UNLOCK, List.of(PREFIX + key), owner); }
        catch (DataAccessException ignored) { /* Lock expires; do not mask the original result. */ }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "계좌 연결 임시 저장소를 사용할 수 없습니다.");
    }
}
