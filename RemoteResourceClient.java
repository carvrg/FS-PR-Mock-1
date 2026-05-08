package com.example.cloud;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * ============================================================================
 *  CLOUD BACKEND ENGINEER — JAVA MOCK INTERVIEW (45 MIN)
 * ============================================================================
 *
 *  Context:
 *  --------
 *  RemoteResourceClient is a small in-process library that wraps calls to a
 *  downstream HTTP service. It is used inside a high-traffic API server
 *  (hundreds of concurrent requests per node). It provides:
 *
 *    - An in-memory cache for recently fetched resources (TTL based).
 *    - A simple "token bucket" rate limiter to protect the downstream.
 *    - An async fetch API that runs the blocking HTTP call on a worker pool.
 *
 *  Three tasks (~15 minutes each):
 *
 *  ┌────────────────────────────────────────────────────────────────────────┐
 *  │ TASK 1 — IMPLEMENT                                                     │
 *  │   Implement `getOrFetch(String key, Function<String,T> loader)`.       │
 *  │   See the method stub below for the full contract.                     │
 *  ├────────────────────────────────────────────────────────────────────────┤
 *  │ TASK 2 — REVIEW & FIX                                                  │
 *  │   The class has correctness bugs that will surface under load.         │
 *  │   Find them, explain why they are bugs, and describe the fix.          │
 *  ├────────────────────────────────────────────────────────────────────────┤
 *  │ TASK 3 — DESIGN CRITIQUE                                               │
 *  │   At the bottom of the file, identify API/design problems that a       │
 *  │   senior engineer would push back on in code review (separate from      │
 *  │   the correctness bugs in Task 2).                                     │
 *  └────────────────────────────────────────────────────────────────────────┘
 *
 *  Ground rules:
 *    - You may use any standard JDK 17+ class.
 *    - You do not need to add new dependencies.
 *    - Pseudocode is fine for any change that would be "obvious to write."
 * ============================================================================
 */
public class RemoteResourceClient<T> {

    // --- Cache ---------------------------------------------------------------

    private final Map<String, CacheEntry<T>> cache = new HashMap<>();
    private final Duration ttl;

    private static final class CacheEntry<T> {
        final T value;
        final Instant expiresAt;
        CacheEntry(T value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }

    // --- Rate limiter (token bucket) -----------------------------------------

    private double tokens;
    private final double maxTokens;
    private final double refillPerSecond;
    private Instant lastRefill;

    // --- Async execution -----------------------------------------------------

    private final ExecutorService executor = Executors.newFixedThreadPool(8);

    // -------------------------------------------------------------------------

    public RemoteResourceClient(Duration ttl, double maxTokens, double refillPerSecond) {
        this.ttl = ttl;
        this.maxTokens = maxTokens;
        this.tokens = maxTokens;
        this.refillPerSecond = refillPerSecond;
        this.lastRefill = Instant.now();
    }

    /**
     * TASK 1 — IMPLEMENT THIS METHOD.
     *
     * Contract:
     *   - If `key` is in the cache and not expired, return the cached value
     *     immediately (no rate-limit check, no loader call).
     *   - Otherwise, attempt to acquire one token from the rate limiter.
     *       - If a token is available, call `loader.apply(key)` to fetch the
     *         value, store it in the cache with `now + ttl`, and return it.
     *       - If no token is available, throw RateLimitedException.
     *   - If the loader throws, propagate the exception. Do NOT cache failures.
     *
     * Assume `loader` is a blocking call to a downstream service.
     */
    public T getOrFetch(String key, Function<String, T> loader) {
        // TODO: implement
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Async wrapper around getOrFetch. Used by the request-handling threads
     * so they don't block on downstream calls.
     */
    public CompletableFuture<T> getOrFetchAsync(String key, Function<String, T> loader) {
        return CompletableFuture.supplyAsync(() -> getOrFetch(key, loader), executor);
    }

    /**
     * Token bucket — refills based on elapsed wall-clock time, then tries to
     * consume one token. Returns true if a token was consumed.
     */
    private boolean tryAcquireToken() {
        Instant now = Instant.now();
        double elapsedSeconds = Duration.between(lastRefill, now).toMillis() / 1000.0;
        tokens = Math.min(maxTokens, tokens + elapsedSeconds * refillPerSecond);
        lastRefill = now;

        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    /** Manually evict a key (e.g., after a known-stale write). */
    public void invalidate(String key) {
        cache.remove(key);
    }

    /** Shut down the worker pool. Called from the application's lifecycle. */
    public void close() {
        executor.shutdown();
    }

    public static class RateLimitedException extends RuntimeException {
        public RateLimitedException() { super("rate limited"); }
    }
}

/*
 * ============================================================================
 *                               YOUR NOTES
 * ============================================================================
 *
 *  TASK 2 — Bugs / correctness issues you found:
 *    1.
 *    2.
 *    3.
 *    (...)
 *
 *  TASK 3 — Design / API problems you'd raise in code review:
 *    1.
 *    2.
 *    3.
 *    (...)
 *
 * ============================================================================
 */
