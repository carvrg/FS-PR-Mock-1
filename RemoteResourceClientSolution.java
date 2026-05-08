package com.example.cloud;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * ============================================================================
 *  REFERENCE SOLUTION — RemoteResourceClient
 * ============================================================================
 *
 *  This file shows:
 *    - Task 1: a clean implementation of `getOrFetch`.
 *    - Task 2: bugs from the candidate file fixed in place, with comments
 *              marked "// FIX:" pointing at each change.
 *    - Task 3: design improvements applied where they don't blow up the size
 *              of the example (Clock injection, single-flight loading,
 *              proper shutdown). Larger design changes are described in
 *              ANSWER_KEY.md rather than implemented.
 *  Note: the public class is named RemoteResourceClientSolution only so this
 *  file can sit next to the candidate file (which already declares a public
 *  class named RemoteResourceClient). Rename it back to RemoteResourceClient
 *  in real use.
 * ============================================================================
 */
public class RemoteResourceClientSolution<T> {

    // FIX (Bug 1): HashMap is not thread-safe. ConcurrentHashMap gives us
    //              lock-free reads and per-bucket locking on writes, plus
    //              atomic computeIfAbsent which we use for single-flight.
    private final Map<String, CacheEntry<T>> cache = new ConcurrentHashMap<>();

    // FIX (Bug 3): Coalesce concurrent loads of the same key. When key K
    //              expires and 50 threads miss at once, only one of them
    //              actually calls the loader; the rest join the same future.
    private final Map<String, CompletableFuture<T>> inFlight = new ConcurrentHashMap<>();

    private final Duration ttl;

    private static final class CacheEntry<T> {
        final T value;
        final Instant expiresAt;
        CacheEntry(T value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
        boolean isFresh(Instant now) {
            return expiresAt.isAfter(now);
        }
    }

    // --- Rate limiter (token bucket) -----------------------------------------

    private double tokens;
    private final double maxTokens;
    private final double refillPerSecond;
    private Instant lastRefill;

    // --- Async execution -----------------------------------------------------

    private final ExecutorService executor;

    // FIX (Design 7): Inject a Clock so tests don't have to Thread.sleep to
    //                 verify TTL or rate-limiter behavior.
    private final Clock clock;

    // -------------------------------------------------------------------------

    public RemoteResourceClientSolution(Duration ttl, double maxTokens, double refillPerSecond) {
        this(ttl, maxTokens, refillPerSecond, Executors.newFixedThreadPool(8), Clock.systemUTC());
    }

    public RemoteResourceClientSolution(Duration ttl,
                                double maxTokens,
                                double refillPerSecond,
                                ExecutorService executor,
                                Clock clock) {
        this.ttl = ttl;
        this.maxTokens = maxTokens;
        this.tokens = maxTokens;
        this.refillPerSecond = refillPerSecond;
        this.executor = executor;
        this.clock = clock;
        this.lastRefill = clock.instant();
    }

    // =========================================================================
    //  TASK 1 — Reference implementation
    // =========================================================================
    /**
     * Returns the cached value if fresh; otherwise consumes a rate-limit
     * token and calls the loader. Failures propagate and are NOT cached.
     *
     * Plain-language walkthrough:
     *   1. Look in the cache. If we have a non-expired entry, return it.
     *      Cache hits don't touch the rate limiter — that's the whole point
     *      of having a cache.
     *   2. On a miss, ask the token bucket for permission. If denied, throw.
     *   3. With permission, call the loader. If it throws, let the exception
     *      bubble up — we never want to cache an error.
     *   4. On success, store value + (now + ttl) and return.
     *
     * Single-flight detail: if many threads miss the same key at the same
     * time, only one calls the loader; the others wait on the same future.
     * This prevents a "thundering herd" of identical downstream calls.
     */
    public T getOrFetch(String key, Function<String, T> loader) {
        // 1. Fast path — cache hit.
        Instant now = clock.instant();
        CacheEntry<T> entry = cache.get(key);
        if (entry != null && entry.isFresh(now)) {
            return entry.value;
        }

        // 2. Cache miss. Coalesce concurrent loads of the same key.
        CompletableFuture<T> myFuture = new CompletableFuture<>();
        CompletableFuture<T> winner = inFlight.putIfAbsent(key, myFuture);

        if (winner != null) {
            // Someone else is already loading this key — wait for their result.
            return winner.join();
        }

        // We are the loader for this key.
        try {
            // 3. Rate-limit check happens once per key per miss, not once per caller.
            if (!tryAcquireToken()) {
                throw new RateLimitedException();
            }

            T value = loader.apply(key);

            // 4. Cache the success.
            cache.put(key, new CacheEntry<>(value, now.plus(ttl)));
            myFuture.complete(value);
            return value;
        } catch (RuntimeException e) {
            myFuture.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, myFuture);
        }
    }

    public CompletableFuture<T> getOrFetchAsync(String key, Function<String, T> loader) {
        return CompletableFuture.supplyAsync(() -> getOrFetch(key, loader), executor);
    }

    // =========================================================================
    //  TASK 2 — Token bucket, fixed
    // =========================================================================
    /**
     * FIX (Bug 2): synchronized makes the read-modify-write of `tokens` and
     *              `lastRefill` atomic. Without this, two threads can both
     *              see tokens == 1.0, both decrement, and both think they
     *              got a token — so the rate limit doesn't actually limit.
     *
     * FIX (Bug 9): elapsed time uses the injected Clock. For production we'd
     *              also consider System.nanoTime() to avoid wall-clock jumps.
     */
    private synchronized boolean tryAcquireToken() {
        Instant now = clock.instant();
        double elapsedSeconds = Duration.between(lastRefill, now).toMillis() / 1000.0;
        tokens = Math.min(maxTokens, tokens + elapsedSeconds * refillPerSecond);
        lastRefill = now;

        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    public void invalidate(String key) {
        cache.remove(key);
    }

    // =========================================================================
    //  TASK 2 — Shutdown, fixed
    // =========================================================================
    /**
     * FIX (Bug 5): the original close() returned immediately; in-flight tasks
     *              kept running and could hold downstream connections open.
     *              The standard JDK shutdown idiom is: stop accepting new
     *              work, wait a bounded time for current work to finish, and
     *              fall back to shutdownNow() if it doesn't.
     */
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    // Log and move on — at this point threads are stuck.
                }
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static class RateLimitedException extends RuntimeException {
        public RateLimitedException() { super("rate limited"); }
    }
}
