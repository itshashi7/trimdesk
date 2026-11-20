package com.groomdesk.logging;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fixed one-minute windows per client address, plus one shared window for everyone so a flood
 * from many (or spoofed) addresses is still capped. Small and in-memory on purpose: it only
 * protects the public crash-report endpoint from being used to flood the logs.
 */
public class ClientErrorRateLimiter {

    private static final long WINDOW_MILLIS = 60_000;
    /** Forget everyone when this many addresses are tracked, so memory stays bounded. */
    private static final int MAX_TRACKED = 10_000;

    private final int perMinute;
    private final int totalPerMinute;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicReference<Window> total = new AtomicReference<>();

    public ClientErrorRateLimiter(int perMinute, int totalPerMinute, Clock clock) {
        this.perMinute = perMinute;
        this.totalPerMinute = totalPerMinute;
        this.clock = clock;
    }

    /** True if this address may send another report now. */
    public boolean tryAcquire(String address) {
        long now = clock.millis();
        if (windows.size() > MAX_TRACKED) {
            windows.clear();
        }
        Window window = windows.compute(address, (key, current) ->
                current == null || now - current.start >= WINDOW_MILLIS ? new Window(now) : current);
        if (window.count.incrementAndGet() > perMinute) {
            return false;
        }
        Window shared = total.updateAndGet(current ->
                current == null || now - current.start >= WINDOW_MILLIS ? new Window(now) : current);
        return shared.count.incrementAndGet() <= totalPerMinute;
    }

    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger();

        Window(long start) {
            this.start = start;
        }
    }
}
