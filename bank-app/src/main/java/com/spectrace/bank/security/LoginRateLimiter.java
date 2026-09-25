package com.spectrace.bank.security;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sliding-window rate limiter for login attempts.
 * Tracks failed attempts per account number; clears on successful login.
 */
@Component
public class LoginRateLimiter {

    private final int maxAttempts;
    private final long windowSeconds;

    /** timestamp (epoch seconds) of each failed attempt, per account number */
    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${bank.security.login-max-attempts:5}") int maxAttempts,
            @Value("${bank.security.login-window-seconds:900}") long windowSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowSeconds = windowSeconds;
    }

    /** Returns true if the account is currently blocked (too many recent failures). */
    public synchronized boolean isBlocked(String accountNumber) {
        purgeExpired(accountNumber);
        Deque<Long> q = failures.getOrDefault(accountNumber, new ArrayDeque<>());
        return q.size() >= maxAttempts;
    }

    /** Record a failed attempt. */
    public synchronized void recordFailure(String accountNumber) {
        failures.computeIfAbsent(accountNumber, k -> new ArrayDeque<>())
                .addLast(Instant.now().getEpochSecond());
    }

    /** Clear the failure counter after a successful login. */
    public synchronized void clearFailures(String accountNumber) {
        failures.remove(accountNumber);
    }

    private void purgeExpired(String accountNumber) {
        Deque<Long> q = failures.get(accountNumber);
        if (q == null) return;
        long cutoff = Instant.now().getEpochSecond() - windowSeconds;
        while (!q.isEmpty() && q.peekFirst() <= cutoff) {
            q.pollFirst();
        }
        if (q.isEmpty()) failures.remove(accountNumber);
    }
}
