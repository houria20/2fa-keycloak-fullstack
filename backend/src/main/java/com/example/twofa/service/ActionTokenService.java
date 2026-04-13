package com.example.twofa.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActionTokenService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.security.action-token-ttl-seconds:300}")
    private long actionTokenTtlSeconds;

    private static final String KEY_PREFIX = "action-token:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Generate and store a one-time action token for a given user and action.
     * The token is stored in Redis with a short TTL and is bound to the user + action.
     */
    public String generateActionToken(String userId, String action) {
        String token = generateSecureToken();
        String redisKey = KEY_PREFIX + token;
        String value = userId + ":" + action;

        redisTemplate.opsForValue().set(redisKey, value, Duration.ofSeconds(actionTokenTtlSeconds));
        log.debug("Generated action token for user={}, action={}, ttl={}s", userId, action, actionTokenTtlSeconds);
        return token;
    }

    /**
     * Validate and atomically consume an action token (one-shot: deleted on first use).
     *
     * @return true if the token was valid, bound to the expected user+action, and successfully consumed
     */
    public boolean validateAndConsumeToken(String token, String userId, String action) {
        if (token == null || token.isBlank()) {
            log.debug("Action token is null or blank");
            return false;
        }

        String redisKey = KEY_PREFIX + token;
        String storedValue = redisTemplate.opsForValue().get(redisKey);

        if (storedValue == null) {
            log.debug("Action token not found or expired: {}", token);
            return false;
        }

        String expectedValue = userId + ":" + action;
        if (!expectedValue.equals(storedValue)) {
            log.warn("Action token mismatch. expected={}, got={}", expectedValue, storedValue);
            return false;
        }

        // Delete atomically to enforce one-time use
        Boolean deleted = redisTemplate.delete(redisKey);
        if (Boolean.TRUE.equals(deleted)) {
            log.debug("Action token consumed for user={}, action={}", userId, action);
            return true;
        }

        log.warn("Could not delete action token (race condition?): {}", token);
        return false;
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
