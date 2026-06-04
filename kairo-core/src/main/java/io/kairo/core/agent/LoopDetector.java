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
 * Six-layer loop detector that identifies repetitive tool call patterns in the ReAct loop.
 *
 * <p><b>Layer 1 — Hash-based detection:</b> Computes a hash of each tool call set (sorted by name
 * with canonicalized arguments). Consecutive identical hashes trigger warnings or hard stops.
 *
 * <p><b>Layer 2 — Frequency-based detection:</b> Tracks per-tool invocation timestamps within a
 * sliding time window. Excessive calls to the same tool within the window trigger alerts.
 *
 * <p><b>Layer 3 — Tool repetition:</b> Detects when the same (tool, args) key appears in every
 * response across a sliding window of consecutive responses.
 *
 * <p><b>Layer 4 — Alternating pattern:</b> Detects A-B-A-B-A-B patterns where an agent bounces
 * between two tools without making progress.
 *
 * <p><b>Layer 5 — No-progress detection:</b> Flags sessions where no "write" tool (edit, write,
 * patch) has been called for N consecutive turns.
 *
 * <p><b>Layer 6 — Context explosion:</b> Detects monotonically growing tool argument payloads,
 * indicating the agent is dumping increasingly large context without making headway.
 *
 * <p>Package-private: not part of the public API.
 */
class LoopDetector {

    static final Set<String> DEFAULT_WRITE_TOOLS =
            Set.of(
                    "write",
                    "edit",
                    "multi_edit",
                    "write_file",
                    "create_file",
                    "patch_file",
                    "apply_diff",
                    "edit_file",
                    "batch_write",
                    "search_replace",
                    "patch_apply",
                    "str_replace_editor");

    private final int hashWarnThreshold;
    private final int hashHardLimit;
    private final int freqWarnThreshold;
    private final int freqHardLimit;
    private final Duration freqWindow;
    private final int toolRepeatHardLimit;
    private final int alternatingWindow;
    private final int noProgressThreshold;
    private final int contextExplosionWindow;
    private final Set<String> writeTools;

    // Layer 1: Hash-based detection — ordered list of call-set hashes
    private final List<Integer> callHashes = new ArrayList<>();

    // Layer 2: Frequency-based detection — sliding time window per tool
    private final Map<String, LinkedList<Long>> recentCalls = new HashMap<>();

    // Layer 3: Tool repetition — per-response (tool, args) key sets for sliding window check
    private final Deque<Set<String>> responseKeyHistory = new ArrayDeque<>();

    // Layer 4-6: Per-turn tracking for alternating / no-progress / context explosion
    private final Deque<List<String>> turnToolKeys = new ArrayDeque<>();
    private final Deque<Set<String>> turnToolNames = new ArrayDeque<>();
    private final Deque<Integer> turnPayloadSizes = new ArrayDeque<>();

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
            int toolRepeatHardLimit,
            int alternatingWindow,
            int noProgressThreshold,
            int contextExplosionWindow,
            Set<String> writeTools) {
        this.hashWarnThreshold = hashWarnThreshold;
        this.hashHardLimit = hashHardLimit;
        this.freqWarnThreshold = freqWarnThreshold;
        this.freqHardLimit = freqHardLimit;
        this.freqWindow = freqWindow;
        this.toolRepeatHardLimit = toolRepeatHardLimit;
        this.alternatingWindow = alternatingWindow;
        this.noProgressThreshold = noProgressThreshold;
        this.contextExplosionWindow = contextExplosionWindow;
        this.writeTools = writeTools;
    }

    LoopDetector(
            int hashWarnThreshold,
            int hashHardLimit,
            int freqWarnThreshold,
            int freqHardLimit,
            Duration freqWindow,
            int toolRepeatHardLimit) {
        this(
                hashWarnThreshold,
                hashHardLimit,
                freqWarnThreshold,
                freqHardLimit,
                freqWindow,
                toolRepeatHardLimit,
                6,
                10,
                4,
                DEFAULT_WRITE_TOOLS);
    }

    /** Create a LoopDetector with sensible defaults. */
    static LoopDetector withDefaults() {
        return new LoopDetector(3, 5, 50, 100, Duration.ofMinutes(10), 4);
    }

    /**
     * Check the given tool calls against all detection layers.
     *
     * @param toolCalls the tool calls from the current model response
     * @return the highest-severity detection result across all layers
     */
    DetectionResult check(List<Content.ToolUseContent> toolCalls) {
        // Record turn-level data for Layers 4-6
        recordTurnData(toolCalls);

        DetectionResult hashResult = checkHash(toolCalls);
        DetectionResult freqResult = checkFrequency(toolCalls);
        DetectionResult repeatResult = checkToolRepetition(toolCalls);
        DetectionResult altResult = checkAlternating();
        DetectionResult noProgResult = checkNoProgress();
        DetectionResult explosionResult = checkContextExplosion();

        return highest(
                hashResult, freqResult, repeatResult, altResult, noProgResult, explosionResult);
    }

    /** Reset all detection state. */
    void reset() {
        callHashes.clear();
        recentCalls.clear();
        responseKeyHistory.clear();
        turnToolKeys.clear();
        turnToolNames.clear();
        turnPayloadSizes.clear();
    }

    private static DetectionResult highest(DetectionResult... results) {
        DetectionResult best = DetectionResult.none();
        for (DetectionResult r : results) {
            if (r.level().ordinal() > best.level().ordinal()) {
                best = r;
            }
        }
        return best;
    }

    private void recordTurnData(List<Content.ToolUseContent> toolCalls) {
        List<String> keys = new ArrayList<>();
        Set<String> names = new HashSet<>();
        int payloadSize = 0;
        for (Content.ToolUseContent tc : toolCalls) {
            String args = tc.input() != null ? tc.input().toString() : "";
            String key = args.length() <= 200 ? args : args.substring(0, 200);
            keys.add(tc.toolName() + ":" + key);
            names.add(tc.toolName());
            payloadSize += args.length();
        }
        turnToolKeys.addLast(keys);
        turnToolNames.addLast(names);
        turnPayloadSizes.addLast(payloadSize);

        int maxHistory =
                Math.max(Math.max(alternatingWindow, noProgressThreshold), contextExplosionWindow);
        while (turnToolKeys.size() > maxHistory) {
            turnToolKeys.removeFirst();
        }
        while (turnToolNames.size() > maxHistory) {
            turnToolNames.removeFirst();
        }
        while (turnPayloadSizes.size() > maxHistory) {
            turnPayloadSizes.removeFirst();
        }
    }

    // ---- Layer 1: Hash-based detection ----

    private DetectionResult checkHash(List<Content.ToolUseContent> toolCalls) {
        int hash = computeCallSetHash(toolCalls);
        callHashes.add(hash);

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
                    "You've called the same tool with identical arguments "
                            + consecutive
                            + " times in a row. The result isn't changing. "
                            + "Pause, re-read the original problem, and try a fundamentally "
                            + "different angle — different tool, different file, or ask the "
                            + "user for clarification.");
        }
        if (consecutive >= hashWarnThreshold) {
            return new DetectionResult(
                    DetectionResult.Level.WARN,
                    "You've repeated the same tool call pattern "
                            + consecutive
                            + " times. The result isn't changing. "
                            + "Try a different approach before continuing.");
        }
        return DetectionResult.none();
    }

    private int computeCallSetHash(List<Content.ToolUseContent> toolCalls) {
        List<String> signatures = new ArrayList<>();
        for (Content.ToolUseContent tc : toolCalls) {
            signatures.add(tc.toolName() + ":" + canonicalizeArgs(tc.input()));
        }
        Collections.sort(signatures);
        return Objects.hash(signatures.toArray());
    }

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

    // ---- Layer 4: Alternating pattern (A-B-A-B-A-B) ----

    private DetectionResult checkAlternating() {
        if (turnToolKeys.size() < alternatingWindow) {
            return DetectionResult.none();
        }
        List<List<String>> recent = lastNFromDeque(turnToolKeys, alternatingWindow);
        for (List<String> turn : recent) {
            if (turn.size() != 1) return DetectionResult.none();
        }
        String a = recent.get(0).get(0);
        String b = recent.get(1).get(0);
        if (a.equals(b)) return DetectionResult.none();

        for (int i = 2; i < alternatingWindow; i++) {
            String expected = (i % 2 == 0) ? a : b;
            if (!expected.equals(recent.get(i).get(0))) return DetectionResult.none();
        }
        String toolA = a.substring(0, a.indexOf(':'));
        String toolB = b.substring(0, b.indexOf(':'));
        return new DetectionResult(
                DetectionResult.Level.HARD_STOP,
                "You're bouncing between '"
                        + toolA
                        + "' and '"
                        + toolB
                        + "' without making progress. "
                        + "Pick one path and commit, or write the fix directly "
                        + "without more exploration.");
    }

    // ---- Layer 5: No-progress detection ----

    private DetectionResult checkNoProgress() {
        if (turnToolNames.size() < noProgressThreshold) {
            return DetectionResult.none();
        }
        List<Set<String>> recent = lastNFromDeque(turnToolNames, noProgressThreshold);
        for (Set<String> names : recent) {
            for (String name : names) {
                if (writeTools.contains(name)) return DetectionResult.none();
            }
        }
        return new DetectionResult(
                DetectionResult.Level.WARN,
                "You've made "
                        + noProgressThreshold
                        + " tool calls without writing any code. "
                        + "Make your best-guess fix now — even a 1-line change is better "
                        + "than another round of exploration. Run the failing test after.");
    }

    // ---- Layer 6: Context explosion ----

    private DetectionResult checkContextExplosion() {
        if (turnPayloadSizes.size() < contextExplosionWindow) {
            return DetectionResult.none();
        }
        List<Integer> recent = lastNFromDeque(turnPayloadSizes, contextExplosionWindow);
        if (recent.get(0) == 0) return DetectionResult.none();

        for (int i = 1; i < contextExplosionWindow; i++) {
            if (recent.get(i) < recent.get(i - 1)) return DetectionResult.none();
        }
        if (recent.get(contextExplosionWindow - 1) < recent.get(0) * 2) {
            return DetectionResult.none();
        }
        return new DetectionResult(
                DetectionResult.Level.WARN,
                "Your tool arguments are growing each turn ("
                        + recent.get(0)
                        + " → "
                        + recent.get(contextExplosionWindow - 1)
                        + " bytes). You're likely stuffing context that isn't helping. "
                        + "Use a smaller, more targeted call: grep for one symbol, "
                        + "read one file, edit one location.");
    }

    private <T> List<T> lastNFromDeque(Deque<T> deque, int n) {
        List<T> result = new ArrayList<>(n);
        int skip = deque.size() - n;
        int i = 0;
        for (T item : deque) {
            if (i >= skip) result.add(item);
            i++;
        }
        return result;
    }
}
