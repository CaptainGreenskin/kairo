/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.kairo.evolution;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks failure patterns across agent executions and triggers evolution when a failure signature
 * recurs beyond a configurable threshold within a sliding time window.
 *
 * <p>Each failure is identified by a {@link FailureSignature} (error type + tool name + abbreviated
 * message). When the same signature occurs {@code threshold} times within {@code window}, the
 * tracker fires and returns the accumulated pattern for evolution review.
 *
 * <p>Thread-safe: uses ConcurrentHashMap + CopyOnWriteArrayList.
 */
public final class FailurePatternTracker {

    private static final int DEFAULT_THRESHOLD = 3;
    private static final Duration DEFAULT_WINDOW = Duration.ofDays(30);

    private final int threshold;
    private final Duration window;
    private final Map<FailureSignature, CopyOnWriteArrayList<Instant>> occurrences =
            new ConcurrentHashMap<>();

    public FailurePatternTracker() {
        this(DEFAULT_THRESHOLD, DEFAULT_WINDOW);
    }

    public FailurePatternTracker(int threshold, Duration window) {
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold must be >= 1");
        }
        this.threshold = threshold;
        this.window = window;
    }

    /**
     * Record a failure occurrence. Returns the failure pattern if the threshold has been reached.
     *
     * @param signature the failure signature
     * @return the accumulated pattern if threshold reached, empty otherwise
     */
    public Optional<FailurePattern> record(FailureSignature signature) {
        Instant now = Instant.now();
        CopyOnWriteArrayList<Instant> timestamps =
                occurrences.computeIfAbsent(signature, k -> new CopyOnWriteArrayList<>());
        timestamps.add(now);

        evictStale(timestamps, now);

        if (timestamps.size() >= threshold) {
            List<Instant> snapshot = List.copyOf(timestamps);
            timestamps.clear();
            return Optional.of(new FailurePattern(signature, snapshot, threshold));
        }
        return Optional.empty();
    }

    /** Return the current occurrence count for a signature within the window. */
    public int countWithinWindow(FailureSignature signature) {
        CopyOnWriteArrayList<Instant> timestamps = occurrences.get(signature);
        if (timestamps == null) {
            return 0;
        }
        evictStale(timestamps, Instant.now());
        return timestamps.size();
    }

    /** Return all signatures that have at least one occurrence in the window. */
    public List<FailureSignature> activeSignatures() {
        Instant cutoff = Instant.now().minus(window);
        List<FailureSignature> active = new ArrayList<>();
        for (Map.Entry<FailureSignature, CopyOnWriteArrayList<Instant>> entry :
                occurrences.entrySet()) {
            if (entry.getValue().stream().anyMatch(t -> t.isAfter(cutoff))) {
                active.add(entry.getKey());
            }
        }
        return Collections.unmodifiableList(active);
    }

    /** Clear all tracked patterns. */
    public void clear() {
        occurrences.clear();
    }

    public int threshold() {
        return threshold;
    }

    public Duration window() {
        return window;
    }

    private void evictStale(CopyOnWriteArrayList<Instant> timestamps, Instant now) {
        Instant cutoff = now.minus(window);
        timestamps.removeIf(t -> t.isBefore(cutoff));
    }

    /** A unique signature identifying a class of failures. */
    public record FailureSignature(String errorType, String toolName, String messagePrefix) {

        public FailureSignature {
            errorType = errorType != null ? errorType : "unknown";
            toolName = toolName != null ? toolName : "unknown";
            messagePrefix = abbreviate(messagePrefix);
        }

        /** Create a signature from an exception and optional tool name. */
        public static FailureSignature fromException(Throwable t, String toolName) {
            String errorType = t.getClass().getSimpleName();
            String msg = t.getMessage() != null ? t.getMessage() : "";
            return new FailureSignature(errorType, toolName, msg);
        }

        private static String abbreviate(String msg) {
            if (msg == null) return "";
            return msg.length() > 100 ? msg.substring(0, 100) : msg;
        }
    }

    /** A failure pattern that has crossed the threshold. */
    public record FailurePattern(
            FailureSignature signature, List<Instant> occurrences, int thresholdReached) {

        public FailurePattern {
            occurrences = List.copyOf(occurrences);
        }

        public String toSummary() {
            return "Failure pattern: "
                    + signature.errorType()
                    + " in "
                    + signature.toolName()
                    + " — '"
                    + signature.messagePrefix()
                    + "' occurred "
                    + occurrences.size()
                    + " time(s) (threshold="
                    + thresholdReached
                    + ")";
        }
    }
}
