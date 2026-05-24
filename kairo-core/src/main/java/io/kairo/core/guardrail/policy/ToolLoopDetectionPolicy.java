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
package io.kairo.core.guardrail.policy;

import io.kairo.api.guardrail.GuardrailContext;
import io.kairo.api.guardrail.GuardrailDecision;
import io.kairo.api.guardrail.GuardrailPayload;
import io.kairo.api.guardrail.GuardrailPhase;
import io.kairo.api.guardrail.GuardrailPolicy;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Mono;

/**
 * Tool-level loop detector. Counts consecutive identical (toolName, args) calls and consecutive
 * same-tool failures, warning at {@code warnThreshold} and denying at {@code blockThreshold}.
 * Promoted from kairo-assistant in M-F5a — this is orthogonal to {@code kairo-core}'s agent-level
 * {@code LoopDetector} (which works on response hashes / frequency); this works on the tool
 * dispatch boundary, before the model even sees the result.
 */
public class ToolLoopDetectionPolicy implements GuardrailPolicy {

    private static final String NAME = "ToolLoopDetectionPolicy";

    private final int warnThreshold;
    private final int blockThreshold;

    private final ConcurrentHashMap<String, AtomicInteger> exactDuplicates =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> sameToolFailures =
            new ConcurrentHashMap<>();

    public ToolLoopDetectionPolicy() {
        this(3, 5);
    }

    public ToolLoopDetectionPolicy(int warnThreshold, int blockThreshold) {
        this.warnThreshold = warnThreshold;
        this.blockThreshold = blockThreshold;
    }

    @Override
    public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
        if (context.phase() == GuardrailPhase.PRE_TOOL) {
            return evaluatePreTool(context);
        }
        if (context.phase() == GuardrailPhase.POST_TOOL) {
            return evaluatePostTool(context);
        }
        return Mono.just(GuardrailDecision.allow(NAME));
    }

    private Mono<GuardrailDecision> evaluatePreTool(GuardrailContext context) {
        if (!(context.payload() instanceof GuardrailPayload.ToolInput toolInput)) {
            return Mono.just(GuardrailDecision.allow(NAME));
        }

        String key = callKey(toolInput.toolName(), toolInput.args());
        int count =
                exactDuplicates.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();

        if (count >= blockThreshold) {
            return Mono.just(
                    GuardrailDecision.deny(
                            "Tool loop detected: "
                                    + toolInput.toolName()
                                    + " called "
                                    + count
                                    + " times with same args",
                            NAME));
        }
        if (count >= warnThreshold) {
            return Mono.just(
                    GuardrailDecision.warn(
                            "Possible tool loop: "
                                    + toolInput.toolName()
                                    + " called "
                                    + count
                                    + " times with same args",
                            NAME));
        }
        return Mono.just(GuardrailDecision.allow(NAME));
    }

    private Mono<GuardrailDecision> evaluatePostTool(GuardrailContext context) {
        if (!(context.payload() instanceof GuardrailPayload.ToolOutput toolOutput)) {
            return Mono.just(GuardrailDecision.allow(NAME));
        }

        if (toolOutput.result() != null && toolOutput.result().isError()) {
            int failCount =
                    sameToolFailures
                            .computeIfAbsent(toolOutput.toolName(), k -> new AtomicInteger(0))
                            .incrementAndGet();

            if (failCount >= blockThreshold) {
                return Mono.just(
                        GuardrailDecision.warn(
                                toolOutput.toolName()
                                        + " has failed "
                                        + failCount
                                        + " times consecutively",
                                NAME));
            }
        } else {
            sameToolFailures.remove(toolOutput.toolName());
        }
        return Mono.just(GuardrailDecision.allow(NAME));
    }

    /** Reset both counters — useful between sessions or when running tests in series. */
    public void reset() {
        exactDuplicates.clear();
        sameToolFailures.clear();
    }

    @Override
    public int order() {
        return -80;
    }

    @Override
    public String name() {
        return NAME;
    }

    private static String callKey(String toolName, Map<String, Object> args) {
        return toolName + "|" + Objects.hashCode(args);
    }
}
