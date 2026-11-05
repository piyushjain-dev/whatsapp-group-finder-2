package com.humlog.whatsapp;

import java.util.concurrent.ThreadLocalRandom;

/** Small helper for human-like random pauses. */
public final class Humanize {

    private Humanize() {
    }

    public static void pause(long minMs, long maxMs) {
        long ms = ThreadLocalRandom.current().nextLong(minMs, maxMs + 1);
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting", e);
        }
    }
}
