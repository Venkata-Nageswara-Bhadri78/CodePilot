package com.developer.copilot.auth.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory mail cooldown and failed-login window. Fine for a single instance;
 * put a gateway limit in front when you run more than one task.
 */
@Component
public class AuthAbuseGuard {

    private final ConcurrentHashMap<String, Long> lastMailEpochMs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Deque<Long>> loginFailures = new ConcurrentHashMap<>();

    public boolean tryAcquireMail(String email, long cooldownMs) {
        if (email == null || cooldownMs <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        Long previous = lastMailEpochMs.get(email);
        if (previous != null && now - previous < cooldownMs) {
            return false;
        }
        lastMailEpochMs.put(email, now);
        return true;
    }

    public boolean isLoginBlocked(String email, int maxFailures, long windowMs) {
        if (email == null || maxFailures <= 0) {
            return false;
        }
        prune(email, windowMs);
        Deque<Long> failures = loginFailures.get(email);
        return failures != null && failures.size() >= maxFailures;
    }

    public void recordLoginFailure(String email, long windowMs) {
        if (email == null) {
            return;
        }
        long now = System.currentTimeMillis();
        loginFailures.compute(email, (key, deque) -> {
            Deque<Long> next = deque == null ? new ArrayDeque<>() : deque;
            next.addLast(now);
            dropExpired(next, now, windowMs);
            return next;
        });
    }

    public void recordLoginSuccess(String email) {
        if (email != null) {
            loginFailures.remove(email);
        }
    }

    private void prune(String email, long windowMs) {
        Deque<Long> failures = loginFailures.get(email);
        if (failures == null) {
            return;
        }
        dropExpired(failures, System.currentTimeMillis(), windowMs);
        if (failures.isEmpty()) {
            loginFailures.remove(email);
        }
    }

    private static void dropExpired(Deque<Long> failures, long now, long windowMs) {
        while (!failures.isEmpty() && now - failures.peekFirst() > windowMs) {
            failures.removeFirst();
        }
    }
}
