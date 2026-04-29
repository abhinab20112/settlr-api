package com.settlr.settlr_api.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis-backed JWT blacklist for logout support.
 *
 * When a user logs out, their token is stored in Redis with a TTL equal to
 * the token's remaining lifetime. This means:
 *   - Blacklisted tokens automatically disappear when they would have expired anyway
 *   - Redis memory stays bounded — no manual cleanup needed
 *   - O(1) lookup per request via GET
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "blacklist:jwt:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Blacklists a token for the given TTL.
     *
     * @param token     the raw JWT string
     * @param ttlMillis remaining time-to-live in milliseconds
     */
    public void blacklist(String token, long ttlMillis) {
        if (ttlMillis <= 0) {
            log.debug("[BLACKLIST] Token already expired — no need to blacklist");
            return;
        }

        String key = KEY_PREFIX + token;
        redisTemplate.opsForValue().set(key, "logout", ttlMillis, TimeUnit.MILLISECONDS);
        log.info("[BLACKLIST] Token blacklisted | ttl={}ms", ttlMillis);
    }

    /**
     * Checks if a token is blacklisted (i.e., the user has logged out).
     *
     * @param token the raw JWT string
     * @return true if the token is in the blacklist
     */
    public boolean isBlacklisted(String token) {
        String key = KEY_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
