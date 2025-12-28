package com.trian.zimswitch.simulator.util;

import java.util.concurrent.atomic.AtomicInteger;

public final class StanGenerator {
    private static final AtomicInteger COUNTER = new AtomicInteger(1);

    private StanGenerator() {}

    /**
     * Generates a 6-digit numeric STAN (1..999999) rolling over.
     */
    public static String nextStan() {
        int val = COUNTER.getAndUpdate(prev -> prev >= 999999 ? 1 : prev + 1);
        return String.format("%06d", val);
    }
}

