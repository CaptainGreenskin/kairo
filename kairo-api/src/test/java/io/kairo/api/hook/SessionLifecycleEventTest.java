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
package io.kairo.api.hook;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.agent.AgentState;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.tool.ToolResult;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SessionLifecycleEventTest {

    @Test
    @DisplayName("SessionStartEvent records all fields correctly")
    void sessionStartEvent_recordFields() {
        Msg input = Msg.of(MsgRole.USER, "hello");
        SessionStartEvent event = new SessionStartEvent("test-agent", input, "gpt-4", 10);

        assertEquals("test-agent", event.agentName());
        assertSame(input, event.input());
        assertEquals("gpt-4", event.modelName());
        assertEquals(10, event.maxIterations());
    }

    @Test
    @DisplayName("SessionEndEvent success case — no error, state COMPLETED")
    void sessionEndEvent_successCase() {
        SessionEndEvent event = new SessionEndEvent(
                "test-agent", AgentState.COMPLETED, 5, 1200L, Duration.ofSeconds(30), null);

        assertEquals("test-agent", event.agentName());
        assertEquals(AgentState.COMPLETED, event.finalState());
        assertEquals(5, event.iterations());
        assertEquals(1200L, event.tokensUsed());
        assertEquals(Duration.ofSeconds(30), event.duration());
        assertNull(event.error());
    }

    @Test
    @DisplayName("SessionEndEvent error case — error present, state FAILED")
    void sessionEndEvent_errorCase() {
        SessionEndEvent event = new SessionEndEvent(
                "test-agent", AgentState.FAILED, 3, 800L, Duration.ofSeconds(10), "timeout");

        assertEquals(AgentState.FAILED, event.finalState());
        assertEquals("timeout", event.error());
        assertNotNull(event.error());
    }

    @Test
    @DisplayName("ToolResultEvent success case — success=true, result accessible")
    void toolResultEvent_successCase() {
        ToolResult toolResult = new ToolResult("use-1", "file created", false, Map.of());
        ToolResultEvent event = new ToolResultEvent("create_file", toolResult, Duration.ofMillis(150), true);

        assertEquals("create_file", event.toolName());
        assertSame(toolResult, event.result());
        assertEquals(Duration.ofMillis(150), event.duration());
        assertTrue(event.success());
        assertFalse(event.result().isError());
    }

    @Test
    @DisplayName("ToolResultEvent failure case — success=false")
    void toolResultEvent_failureCase() {
        ToolResult toolResult = new ToolResult("use-2", "file not found", true, Map.of());
        ToolResultEvent event = new ToolResultEvent("read_file", toolResult, Duration.ofMillis(50), false);

        assertEquals("read_file", event.toolName());
        assertFalse(event.success());
        assertTrue(event.result().isError());
        assertEquals("file not found", event.result().content());
    }

    @Test
    @DisplayName("HookChain default fire methods return the event unchanged")
    void hookChain_defaultFireMethods() {
        // Use a minimal HookChain that only implements required abstract methods
        HookChain minimalChain = new HookChain() {
            @Override
            public void register(Object hookHandler) {}

            @Override
            public void unregister(Object hookHandler) {}

            @Override
            public <T> reactor.core.publisher.Mono<T> firePreReasoning(T event) {
                return reactor.core.publisher.Mono.just(event);
            }

            @Override
            public <T> reactor.core.publisher.Mono<T> firePostReasoning(T event) {
                return reactor.core.publisher.Mono.just(event);
            }

            @Override
            public <T> reactor.core.publisher.Mono<T> firePreActing(T event) {
                return reactor.core.publisher.Mono.just(event);
            }

            @Override
            public <T> reactor.core.publisher.Mono<T> firePostActing(T event) {
                return reactor.core.publisher.Mono.just(event);
            }

            @Override
            public <T> reactor.core.publisher.Mono<T> firePreCompact(T event) {
                return reactor.core.publisher.Mono.just(event);
            }

            @Override
            public <T> reactor.core.publisher.Mono<T> firePostCompact(T event) {
                return reactor.core.publisher.Mono.just(event);
            }
        };

        Msg input = Msg.of(MsgRole.USER, "test");
        SessionStartEvent startEvent = new SessionStartEvent("agent", input, "gpt-4", 5);
        SessionEndEvent endEvent = new SessionEndEvent(
                "agent", AgentState.COMPLETED, 3, 500L, Duration.ofSeconds(10), null);
        ToolResult tr = new ToolResult("id", "ok", false, Map.of());
        ToolResultEvent toolEvent = new ToolResultEvent("tool", tr, Duration.ofMillis(100), true);

        // Default methods should return the event unchanged (using block())
        assertSame(startEvent, minimalChain.fireOnSessionStart(startEvent).block());
        assertSame(endEvent, minimalChain.fireOnSessionEnd(endEvent).block());
        assertSame(toolEvent, minimalChain.fireOnToolResult(toolEvent).block());
    }
}
