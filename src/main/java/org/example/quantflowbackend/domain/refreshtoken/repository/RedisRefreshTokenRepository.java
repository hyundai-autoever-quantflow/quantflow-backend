package org.example.quantflowbackend.domain.refreshtoken.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RedisRefreshTokenRepository {
    private static final String PREFIX = "quantflow:refresh-token:";
    private static final DefaultRedisScript<Long> ROTATE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 0
            end
            redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> DELETE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then
                return 0
            end
            return redis.call('DEL', KEYS[1])
            """, Long.class);
    private final StringRedisTemplate redisTemplate;

    public void save(Long userId, String token, Duration ttl) {
        validateTtl(ttl);
        redisTemplate.opsForValue().set(PREFIX + userId, hash(token), ttl);
    }

    public boolean rotate(Long userId, String oldToken, String newToken, Duration ttl) {
        validateTtl(ttl);
        Long result = redisTemplate.execute(ROTATE, List.of(PREFIX + userId),
                hash(oldToken), hash(newToken), Long.toString(ttl.toMillis()));
        return Long.valueOf(1).equals(result);
    }

    public boolean deleteIfMatches(Long userId, String token) {
        return Long.valueOf(1).equals(redisTemplate.execute(
                DELETE, List.of(PREFIX + userId), hash(token)
        ));
    }

    private void validateTtl(Duration ttl) {
        if (ttl == null || ttl.toMillis() <= 0) throw new IllegalArgumentException("유효한 TTL이 필요합니다.");
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
