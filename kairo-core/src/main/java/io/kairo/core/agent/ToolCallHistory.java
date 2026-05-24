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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Tracks recent tool calls to detect repeated (toolName, argsHash) pairs within a sliding window.
 *
 * <p><b>Layer 0 of the loop-detection stack — per-call granularity.</b> Counts how many of the last
 * N <i>individual</i> tool calls are identical. Catches tight repair loops where the agent emits
 * one call per response and the same call keeps coming back: {@code bash("mvn test")} → fail → same
 * fix → same fail.
 *
 * <p>Not a duplicate of {@link LoopDetector} — that one operates at <b>per-response granularity</b>
 * (Layers 1-3): hash of the whole tool-call set, frequency sliding window, and "every response in
 * the last N contains the same key" detection. {@link LoopDetector}#checkToolRepetition catches
 * <i>this</i> pattern that {@code ToolCallHistory} would miss:
 *
 * <pre>
 *   resp 1: [bash("foo"), read("bar")]
 *   resp 2: [bash("foo"), edit("baz")]
 *   resp 3: [bash("foo"), grep("qux")]   ← bash("foo") horizontal across responses
 * </pre>
 *
 * <p>Both layers are intentionally registered in {@code ReActLoop.checkLoops()} — see lines 495-501
 * for the layered dispatch. Do NOT delete on a "duplicate detection" sweep.
 *
 * <p>Thread-unsafe — single-agent use only.
 */
public final class ToolCallHistory {

    private static final String ENV_WARN_AT = "KAIRO_TOOL_LOOP_WARN_AT";
    private static final String ENV_ABORT_AT = "KAIRO_TOOL_LOOP_ABORT_AT";

    private static final int DEFAULT_WARN_AT = 3;
    private static final int DEFAULT_ABORT_AT = 5;

    private final int warnAt;
    private final int abortAt;

    /** Ring buffer of (toolName:argsHash) keys. */
    private final Deque<String> recent = new ArrayDeque<>();

    public ToolCallHistory() {
        this(
                resolveIntEnv(ENV_WARN_AT, DEFAULT_WARN_AT),
                resolveIntEnv(ENV_ABORT_AT, DEFAULT_ABORT_AT));
    }

    public ToolCallHistory(int warnAt, int abortAt) {
        if (warnAt <= 0 || abortAt <= 0 || warnAt >= abortAt) {
            throw new IllegalArgumentException(
                    "warnAt must be positive and less than abortAt: warnAt="
                            + warnAt
                            + ", abortAt="
                            + abortAt);
        }
        this.warnAt = warnAt;
        this.abortAt = abortAt;
    }

    public enum Status {
        OK,
        WARN,
        ABORT
    }

    /**
     * Records a tool call and returns the loop status.
     *
     * @param toolName tool name
     * @param argsJson serialised arguments (used only for hash)
     */
    public Status record(String toolName, String argsJson) {
        String key = toolName + ":" + Objects.hashCode(argsJson);
        recent.addLast(key);
        while (recent.size() > abortAt) {
            recent.removeFirst();
        }

        long count = recent.stream().filter(key::equals).count();
        if (count >= abortAt) {
            return Status.ABORT;
        }
        if (count >= warnAt) {
            return Status.WARN;
        }
        return Status.OK;
    }

    /** Returns the warn threshold. */
    public int warnAt() {
        return warnAt;
    }

    /** Returns the abort threshold. */
    public int abortAt() {
        return abortAt;
    }

    /** Reset history (e.g., after a different tool call breaks the streak). */
    public void reset() {
        recent.clear();
    }

    private static int resolveIntEnv(String envVar, int defaultValue) {
        String value = System.getenv(envVar);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException ignored) {
                // Fall through to default
            }
        }
        return defaultValue;
    }
}
