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
package io.kairo.core.agent;

import io.kairo.api.message.Content;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dual-layer loop detector that identifies repetitive tool call patterns in the ReAct loop.
 *
 * <p><b>Layer 1 — Hash-based detection:</b> Computes a hash of each tool call set (sorted by name
 * with canonicalized arguments). Consecutive identical hashes trigger warnings or hard stops.
 *
 * <p><b>Layer 2 — Frequency-based detection:</b> Tracks per-tool invocation timestamps within a
 * sliding time window. Excessive calls to the same tool within the window trigger alerts.
 *
 * <p>Package-private: not part of the public API.
 */
class LoopDetector {

    private final int hashWarnThreshold;
    private final int hashHardLimit;
    private final int freqWarnThreshold;
    private final int freqHardLimit;
    private final Duration freqWindow;
    private final int toolRepeatHardLimit;

    // Layer 1: Hash-based detection — ordered list of call-set hashes
    private final List<Integer> callHashes = new ArrayList<>();

    // Layer 2: Frequency-based detection — sliding time window per tool
    private final Map<String, LinkedList<Long>> recentCalls = new HashMap<>();

    // Layer 3: Tool repetition — per-response (tool, args) key sets for sliding window check
    private final Deque<Set<String>> responseKeyHistory = new ArrayDeque<>();

    private record ToolCallKey(String toolName, String canonicalArgs) {
        @Override
        public String toString() {
            return toolName + ":" + canonicalArgs;
        }
    }

    /**
     * Result of a loop detection check.
     *
     * @param level the severity level
     * @param message a human-readable description of the detection (empty for NONE)
     */
    record DetectionResult(Level level, String message) {
        enum Level {
            NONE,
            WARN,
            HARD_STOP
        }

        static DetectionResult none() {
            return new DetectionResult(Level.NONE, "");
        }
    }

    LoopDetector(
            int hashWarnThreshold,
            int hashHardLimit,
            int freqWarnThreshold,
            int freqHardLimit,
            Duration freqWindow,
            int toolRepeatHardLimit) {
        this.hashWarnThreshold = hashWarnThreshold;
        this.hashHardLimit = hashHardLimit;
        this.freqWarnThreshold = freqWarnThreshold;
        this.freqHardLimit = freqHardLimit;
        this.freqWindow = freqWindow;
        this.toolRepeatHardLimit = toolRepeatHardLimit;
    }

    /** Create a LoopDetector with sensible defaults. */
    static LoopDetector withDefaults() {
        return new LoopDetector(3, 5, 50, 100, Duration.ofMinutes(10), 4);
    }

    /**
     * Check the given tool calls against both detection layers.
     *
     * @param toolCalls the tool calls from the current model response
     * @return the highest-severity detection result across both layers
     */
    DetectionResult check(List<Content.ToolUseContent> toolCalls) {
        DetectionResult hashResult = checkHash(toolCalls);
        DetectionResult freqResult = checkFrequency(toolCalls);
        DetectionResult repeatResult = checkToolRepetition(toolCalls);

        // Return highest severity: HARD_STOP > WARN > NONE
        DetectionResult best =
                hashResult.level().ordinal() >= freqResult.level().ordinal()
                        ? (hashResult.level() == DetectionResult.Level.NONE
                                ? freqResult
                                : hashResult)
                        : freqResult;
        return repeatResult.level().ordinal() > best.level().ordinal() ? repeatResult : best;
    }

    /** Reset all detection state. */
    void reset() {
        callHashes.clear();
        recentCalls.clear();
        responseKeyHistory.clear();
    }

    // ---- Layer 1: Hash-based detection ----

    private DetectionResult checkHash(List<Content.ToolUseContent> toolCalls) {
        int hash = computeCallSetHash(toolCalls);
        callHashes.add(hash);

        // Count consecutive identical hashes from the end
        int consecutive = 0;
        for (int i = callHashes.size() - 1; i >= 0; i--) {
            if (callHashes.get(i) == hash) {
                consecutive++;
            } else {
                break;
            }
        }

        if (consecutive >= hashHardLimit) {
            return new DetectionResult(
                    DetectionResult.Level.HARD_STOP,
                    "Loop detected: identical tool call pattern repeated "
                            + consecutive
                            + " times consecutively (hard limit: "
                            + hashHardLimit
                            + ")");
        }
        if (consecutive >= hashWarnThreshold) {
            return new DetectionResult(
                    DetectionResult.Level.WARN,
                    "Possible loop: identical tool call pattern repeated "
                            + consecutive
                            + " times consecutively. Consider a different approach.");
        }
        return DetectionResult.none();
    }

    /**
     * Compute a stable hash for a set of tool calls. Tool calls are sorted by name, and each tool's
     * arguments are canonicalized by sorting map entries.
     */
    private int computeCallSetHash(List<Content.ToolUseContent> toolCalls) {
        List<String> signatures = new ArrayList<>();
        for (Content.ToolUseContent tc : toolCalls) {
            signatures.add(tc.toolName() + ":" + canonicalizeArgs(tc.input()));
        }
        Collections.sort(signatures);
        return Objects.hash(signatures.toArray());
    }

    /** Canonicalize a tool input map into a stable, sorted string representation. */
    private String canonicalizeArgs(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return "{}";
        }
        var sorted = new TreeMap<>(input);
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : sorted.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(entry.getKey()).append("=").append(canonicalizeValue(entry.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String canonicalizeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            return canonicalizeArgs((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(canonicalizeValue(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return value.toString();
    }

    // ---- Layer 2: Frequency-based detection (sliding time window) ----

    private DetectionResult checkFrequency(List<Content.ToolUseContent> toolCalls) {
        long now = System.currentTimeMillis();
        DetectionResult worst = DetectionResult.none();

        for (Content.ToolUseContent tc : toolCalls) {
            String toolName = tc.toolName();
            recentCalls.computeIfAbsent(toolName, k -> new LinkedList<>()).add(now);
            evictOld(toolName);

            int count = recentCalls.get(toolName).size();

            if (count >= freqHardLimit) {
                return new DetectionResult(
                        DetectionResult.Level.HARD_STOP,
                        "Loop detected: tool '"
                                + toolName
                                + "' called "
                                + count
                                + " times within "
                                + freqWindow.toMinutes()
                                + " minutes (hard limit: "
                                + freqHardLimit
                                + ")");
            }
            if (count >= freqWarnThreshold && worst.level() == DetectionResult.Level.NONE) {
                worst =
                        new DetectionResult(
                                DetectionResult.Level.WARN,
                                "High frequency: tool '"
                                        + toolName
                                        + "' called "
                                        + count
                                        + " times within "
                                        + freqWindow.toMinutes()
                                        + " minutes. Consider a different approach.");
            }
        }

        return worst;
    }

    private void evictOld(String toolName) {
        var calls = recentCalls.get(toolName);
        if (calls != null) {
            long cutoff = System.currentTimeMillis() - freqWindow.toMillis();
            calls.removeIf(t -> t < cutoff);
        }
    }

    // ---- Layer 3: Tool repetition (same tool+args in N consecutive responses) ----

    private DetectionResult checkToolRepetition(List<Content.ToolUseContent> toolCalls) {
        if (toolRepeatHardLimit <= 0) {
            return DetectionResult.none();
        }
        Set<String> currentKeys =
                toolCalls.stream()
                        .map(
                                tc ->
                                        new ToolCallKey(tc.toolName(), canonicalizeArgs(tc.input()))
                                                .toString())
                        .collect(Collectors.toCollection(LinkedHashSet::new));

        responseKeyHistory.addLast(currentKeys);
        while (responseKeyHistory.size() > toolRepeatHardLimit) {
            responseKeyHistory.removeFirst();
        }
        if (responseKeyHistory.size() < toolRepeatHardLimit) {
            return DetectionResult.none();
        }

        // Find any key that appears in every response in the sliding window
        for (String key : currentKeys) {
            boolean allContain = true;
            for (Set<String> responseKeys : responseKeyHistory) {
                if (!responseKeys.contains(key)) {
                    allContain = false;
                    break;
                }
            }
            if (allContain) {
                String toolName = key.substring(0, key.indexOf(':'));
                return new DetectionResult(
                        DetectionResult.Level.HARD_STOP,
                        "Loop detected: tool '"
                                + toolName
                                + "' called with identical arguments in "
                                + toolRepeatHardLimit
                                + " consecutive responses");
            }
        }
        return DetectionResult.none();
    }
}
