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
package io.kairo.api.hook.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.agent.AgentState;
import io.kairo.api.hook.*;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.ToolResult;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * Contract-level tests for the Hook API surface in kairo-api.
 *
 * <p>Validates HookResult behavior, Decision priority ordering, hook event type completeness,
 * and HookChain default method contracts.
 */
@Tag("integration")
class HookContractTest {

    // ================================
    // HookResult serialization / round-trip
    // ================================

    @Test
    @DisplayName("HookResult record preserves all fields through construction")
    void hookResult_recordFieldsRoundTrip() {
        Msg msg = Msg.of(MsgRole.ASSISTANT, "injected");
        Map<String, Object> input = Map.of("key", "value");
        HookResult<String> result =
                new HookResult<>("evt", HookResult.Decision.INJECT, "ctx", input, "reason", msg, "src");

        assertEquals("evt", result.event());
        assertEquals(HookResult.Decision.INJECT, result.decision());
        assertEquals("ctx", result.injectedContext());
        assertEquals(input, result.modifiedInput());
        assertEquals("reason", result.reason());
        assertSame(msg, result.injectedMessage());
        assertEquals("src", result.hookSource());
    }

    @Test
    @DisplayName("HookResult record equality follows value semantics")
    void hookResult_valueEquality() {
        HookResult<String> a = HookResult.proceed("event");
        HookResult<String> b = HookResult.proceed("event");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        HookResult<String> c = HookResult.abort("event", "reason");
        assertNotEquals(a, c);
    }

    // ================================
    // Decision enum completeness and priority ordering
    // ================================

    @Test
    @DisplayName("Decision enum contains exactly 5 values: CONTINUE, INJECT, MODIFY, SKIP, ABORT")
    void decision_enumCompleteness() {
        HookResult.Decision[] values = HookResult.Decision.values();
        assertEquals(5, values.length);

        Set<String> names =
                Arrays.stream(values).map(Enum::name).collect(Collectors.toSet());
        assertTrue(names.containsAll(Set.of("CONTINUE", "INJECT", "MODIFY", "SKIP", "ABORT")));
    }

    @Test
    @DisplayName("Decision priority ordering: CONTINUE < INJECT < MODIFY < SKIP < ABORT")
    void decision_priorityOrdering() {
        HookResult.Decision[] ordered = {
            HookResult.Decision.CONTINUE,
            HookResult.Decision.INJECT,
            HookResult.Decision.MODIFY,
            HookResult.Decision.SKIP,
            HookResult.Decision.ABORT
        };
        for (int i = 0; i < ordered.length - 1; i++) {
            assertTrue(
                    ordered[i].priority() < ordered[i + 1].priority(),
                    ordered[i] + " should have lower priority than " + ordered[i + 1]);
        }
    }

    @Test
    @DisplayName("Each Decision has a unique, non-negative priority value")
    void decision_uniqueNonNegativePriorities() {
        Set<Integer> priorities =
                Arrays.stream(HookResult.Decision.values())
                        .map(HookResult.Decision::priority)
                        .collect(Collectors.toSet());
        assertEquals(HookResult.Decision.values().length, priorities.size(), "Priorities must be unique");
        assertTrue(priorities.stream().allMatch(p -> p >= 0), "All priorities must be non-negative");
    }

    // ================================
    // Hook event types completeness
    // ================================

    @Test
    @DisplayName("All expected hook event record types are instantiable")
    void hookEventTypes_allInstantiable() {
        Msg msg = Msg.of(MsgRole.USER, "hello");
        ModelConfig config =
                ModelConfig.builder().model(ModelConfig.DEFAULT_MODEL).build();

        // Pre/Post reasoning events
        PreReasoningEvent preReasoning = new PreReasoningEvent(List.of(msg), config, false);
        assertNotNull(preReasoning);
        assertFalse(preReasoning.cancelled());

        PostReasoningEvent postReasoning = new PostReasoningEvent(null, false);
        assertNotNull(postReasoning);

        // Pre/Post acting events
        PreActingEvent preActing = new PreActingEvent("bash", Map.of("cmd", "ls"), false);
        assertNotNull(preActing);
        assertEquals("bash", preActing.toolName());

        PostActingEvent postActing = new PostActingEvent("bash", null);
        assertNotNull(postActing);

        // Pre/Post compact events
        PreCompactEvent preCompact = new PreCompactEvent(List.of(msg), 0.8);
        assertNotNull(preCompact);
        assertEquals(0.8, preCompact.pressure());
        assertFalse(preCompact.cancelled());

        PostCompactEvent postCompact = new PostCompactEvent(List.of(msg), 500, "tail-drop", List.of());
        assertNotNull(postCompact);
        assertEquals(500, postCompact.tokensSaved());

        // Session lifecycle events
        SessionStartEvent sessionStart = new SessionStartEvent("agent", msg, "claude", 100);
        assertNotNull(sessionStart);
        assertEquals("agent", sessionStart.agentName());

        SessionEndEvent sessionEnd =
                new SessionEndEvent("agent", AgentState.COMPLETED, 5, 1000L, Duration.ofSeconds(30), null);
        assertNotNull(sessionEnd);
        assertNull(sessionEnd.error());

        // Tool result event
        ToolResultEvent toolResult =
                new ToolResultEvent("bash", null, Duration.ofMillis(200), true);
        assertNotNull(toolResult);
        assertTrue(toolResult.success());
    }

    // ================================
    // HookChain default method contracts
    // ================================

    @Test
    @DisplayName("HookChain.firePreActingWithResult wraps event as CONTINUE by default")
    void hookChain_firePreActingWithResult_defaultBehavior() {
        HookChain chain = createPassthroughChain();
        PreActingEvent event = new PreActingEvent("bash", Map.of(), false);

        HookResult<PreActingEvent> result = chain.firePreActingWithResult(event).block();
        assertNotNull(result);
        assertEquals(HookResult.Decision.CONTINUE, result.decision());
        assertSame(event, result.event());
        assertTrue(result.shouldProceed());
        assertFalse(result.shouldSkip());
    }

    @Test
    @DisplayName("HookChain default session/tool methods return event unchanged")
    void hookChain_defaultSessionAndToolMethods_passthrough() {
        HookChain chain = createPassthroughChain();
        Msg msg = Msg.of(MsgRole.USER, "test");
        SessionStartEvent startEvent = new SessionStartEvent("agent", msg, "model", 10);

        assertSame(startEvent, chain.fireOnSessionStart(startEvent).block());

        SessionEndEvent endEvent =
                new SessionEndEvent("agent", AgentState.COMPLETED, 1, 100L, Duration.ofSeconds(1), null);
        assertSame(endEvent, chain.fireOnSessionEnd(endEvent).block());

        ToolResultEvent toolEvent =
                new ToolResultEvent("read", null, Duration.ofMillis(50), true);
        assertSame(toolEvent, chain.fireOnToolResult(toolEvent).block());
    }

    @Test
    @DisplayName("HookChain WithResult default methods all produce CONTINUE decisions")
    void hookChain_allWithResultDefaults_produceContinue() {
        HookChain chain = createPassthroughChain();
        String event = "test-event";

        // All six *WithResult defaults should wrap as CONTINUE
        assertEquals(HookResult.Decision.CONTINUE,
                chain.firePreReasoningWithResult(event).block().decision());
        assertEquals(HookResult.Decision.CONTINUE,
                chain.firePostReasoningWithResult(event).block().decision());
        assertEquals(HookResult.Decision.CONTINUE,
                chain.firePreActingWithResult(event).block().decision());
        assertEquals(HookResult.Decision.CONTINUE,
                chain.firePostActingWithResult(event).block().decision());
        assertEquals(HookResult.Decision.CONTINUE,
                chain.firePreCompactWithResult(event).block().decision());
        assertEquals(HookResult.Decision.CONTINUE,
                chain.firePostCompactWithResult(event).block().decision());
    }

    // ================================
    // HookResult factory method contracts
    // ================================

    @Test
    @DisplayName("All HookResult factory methods produce correct decisions")
    void hookResult_factoryMethodDecisions() {
        Msg msg = Msg.of(MsgRole.ASSISTANT, "hi");

        assertEquals(HookResult.Decision.CONTINUE, HookResult.proceed("e").decision());
        assertEquals(HookResult.Decision.ABORT, HookResult.abort("e", "r").decision());
        assertEquals(HookResult.Decision.MODIFY, HookResult.modify("e", Map.of()).decision());
        assertEquals(HookResult.Decision.CONTINUE, HookResult.withContext("e", "ctx").decision());
        assertEquals(HookResult.Decision.SKIP, HookResult.skip("e", "r").decision());
        assertEquals(HookResult.Decision.INJECT, HookResult.inject("e", msg, "src").decision());
    }

    @Test
    @DisplayName("HookResult boolean helpers are consistent across all factory methods")
    void hookResult_booleanHelperConsistency() {
        // proceed: all false
        HookResult<String> proceed = HookResult.proceed("e");
        assertTrue(proceed.shouldProceed());
        assertFalse(proceed.shouldSkip());
        assertFalse(proceed.hasModifiedInput());
        assertFalse(proceed.hasInjectedContext());
        assertFalse(proceed.hasInjectedMessage());

        // abort: not proceed
        HookResult<String> abort = HookResult.abort("e", "r");
        assertFalse(abort.shouldProceed());
        assertFalse(abort.shouldSkip());

        // skip: proceed but shouldSkip
        HookResult<String> skip = HookResult.skip("e", "r");
        assertTrue(skip.shouldProceed());
        assertTrue(skip.shouldSkip());

        // modify: hasModifiedInput
        HookResult<String> modify = HookResult.modify("e", Map.of("k", "v"));
        assertTrue(modify.shouldProceed());
        assertTrue(modify.hasModifiedInput());

        // withContext: hasInjectedContext
        HookResult<String> withCtx = HookResult.withContext("e", "context");
        assertTrue(withCtx.hasInjectedContext());

        // inject: hasInjectedMessage
        Msg msg = Msg.of(MsgRole.ASSISTANT, "hi");
        HookResult<String> inject = HookResult.inject("e", msg, "src");
        assertTrue(inject.hasInjectedMessage());
    }

    // ================================
    // Helpers
    // ================================

    /** Creates a minimal HookChain that passes events through unchanged. */
    private HookChain createPassthroughChain() {
        return new HookChain() {
            @Override
            public void register(Object hookHandler) {}

            @Override
            public void unregister(Object hookHandler) {}

            @Override
            public <T> Mono<T> firePreReasoning(T event) {
                return Mono.just(event);
            }

            @Override
            public <T> Mono<T> firePostReasoning(T event) {
                return Mono.just(event);
            }

            @Override
            public <T> Mono<T> firePreActing(T event) {
                return Mono.just(event);
            }

            @Override
            public <T> Mono<T> firePostActing(T event) {
                return Mono.just(event);
            }

            @Override
            public <T> Mono<T> firePreCompact(T event) {
                return Mono.just(event);
            }

            @Override
            public <T> Mono<T> firePostCompact(T event) {
                return Mono.just(event);
            }
        };
    }
}
