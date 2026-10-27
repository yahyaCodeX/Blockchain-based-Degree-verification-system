package com.decentralized.degree.vault.decentralizeddegreevault.Service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force rate limiter for Student Vault authentication.
 * Limits failed PIN attempts to 5 before enforcing a 15-minute temporary lockout.
 */
@Slf4j
@Service
public class LoginRateLimiterService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000; // 15 minutes

    private static class AttemptMetadata {
        int attempts;
        long lastAttemptTime;
        long lockoutUntil;

        AttemptMetadata() {
            this.attempts = 1;
            this.lastAttemptTime = System.currentTimeMillis();
            this.lockoutUntil = 0;
        }
    }

    private final ConcurrentHashMap<String, AttemptMetadata> attemptsCache = new ConcurrentHashMap<>();

    /**
     * Checks if the identifier (roll number or email) is currently locked out.
     */
    public boolean isLocked(String identifier) {
        if (identifier == null) return false;
        String key = identifier.trim().toLowerCase();
        AttemptMetadata metadata = attemptsCache.get(key);

        if (metadata == null) return false;

        long now = System.currentTimeMillis();
        if (metadata.lockoutUntil > now) {
            return true;
        }

        // Lockout expired; reset if needed
        if (metadata.lockoutUntil != 0 && metadata.lockoutUntil <= now) {
            attemptsCache.remove(key);
            return false;
        }

        return false;
    }

    /**
     * Gets the remaining lockout time in minutes (minimum 1 minute).
     */
    public long getRemainingLockoutMinutes(String identifier) {
        if (identifier == null) return 0;
        String key = identifier.trim().toLowerCase();
        AttemptMetadata metadata = attemptsCache.get(key);

        if (metadata == null || metadata.lockoutUntil <= System.currentTimeMillis()) {
            return 0;
        }

        long remainingMs = metadata.lockoutUntil - System.currentTimeMillis();
        return Math.max(1, (remainingMs + 59999) / 60000);
    }

    /**
     * Records a failed PIN login attempt.
     * Enforces lockout if attempts reach MAX_ATTEMPTS.
     */
    public int recordFailedAttempt(String identifier) {
        if (identifier == null) return 0;
        String key = identifier.trim().toLowerCase();
        long now = System.currentTimeMillis();

        AttemptMetadata metadata = attemptsCache.compute(key, (k, existing) -> {
            if (existing == null) {
                return new AttemptMetadata();
            }

            // If last attempt was more than 15 mins ago and not locked, reset count
            if (existing.lockoutUntil == 0 && (now - existing.lastAttemptTime > LOCKOUT_DURATION_MS)) {
                existing.attempts = 1;
            } else {
                existing.attempts++;
            }

            existing.lastAttemptTime = now;

            if (existing.attempts >= MAX_ATTEMPTS) {
                existing.lockoutUntil = now + LOCKOUT_DURATION_MS;
                log.warn("Brute-force security lockout triggered for student ID [{}]. Locked until: {}", key, existing.lockoutUntil);
            }

            return existing;
        });

        return metadata != null ? metadata.attempts : 1;
    }

    /**
     * Resets failed attempts upon successful authentication.
     */
    public void recordSuccessfulLogin(String identifier) {
        if (identifier != null) {
            attemptsCache.remove(identifier.trim().toLowerCase());
        }
    }
}
