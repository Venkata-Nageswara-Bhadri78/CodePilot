package com.developer.copilot.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;

/**
 * Micrometer counters for the shared kernel. Never throws — metrics must not break a request.
 */
public final class CopilotMetrics {

    private CopilotMetrics() {
    }

    public static void increment(String name, String... tagKeyValues) {
        try {
            Counter.Builder builder = Counter.builder(name);
            for (int i = 0; i + 1 < tagKeyValues.length; i += 2) {
                builder.tag(tagKeyValues[i], tagKeyValues[i + 1]);
            }
            builder.register(Metrics.globalRegistry).increment();
        } catch (RuntimeException ignored) {
            // ignore
        }
    }
}
