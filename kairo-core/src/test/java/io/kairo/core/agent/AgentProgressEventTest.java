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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.event.AgentProgressEvent;
import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.KairoEventBus;
import io.kairo.api.hook.HookChain;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.tool.DefaultPermissionGuard;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.DefaultToolRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

class AgentProgressEventTest {

    private ModelProvider modelProvider;
    private DefaultToolRegistry toolRegistry;
    private ToolExecutor toolExecutor;
    private HookChain hookChain;

    @BeforeEach
    void setUp() {
        modelProvider = mock(ModelProvider.class);
        toolRegistry = new DefaultToolRegistry();
        toolExecutor = new DefaultToolExecutor(toolRegistry, new DefaultPermissionGuard());
        hookChain = new DefaultHookChain();
    }

    private AgentConfig.Builder baseConfig() {
        return AgentConfig.builder()
                .name("test-agent")
                .modelProvider(modelProvider)
                .toolRegistry(toolRegistry)
                .modelName("test-model")
                .maxIterations(10)
                .timeout(Duration.ofSeconds(30))
                .tokenBudget(100_000);
    }

    private ModelResponse textResponse(String text) {
        return new ModelResponse(
                "resp-1",
                List.of(new Content.TextContent(text)),
                new ModelResponse.Usage(10, 20, 0, 0),
                ModelResponse.StopReason.END_TURN,
                "test-model");
    }

    // ---- AgentProgressEvent record tests ----

    @Test
    void agentProgressEvent_fieldsAreCorrect() {
        AgentProgressEvent event =
                new AgentProgressEvent(
                        "my-agent", AgentProgressEvent.Phase.ITERATION_START, 1, null, 42L, 0, 0);

        assertEquals("my-agent", event.agentName());
        assertEquals(AgentProgressEvent.Phase.ITERATION_START, event.phase());
        assertEquals(1, event.iteration());
        assertNull(event.detail());
        assertEquals(42L, event.elapsedMs());
        assertEquals(0, event.inputTokens());
        assertEquals(0, event.outputTokens());
    }

    @Test
    void phase_enumContainsAllExpectedValues() {
        AgentProgressEvent.Phase[] phases = AgentProgressEvent.Phase.values();
        assertEquals(5, phases.length);
        assertArrayEquals(
                new AgentProgressEvent.Phase[] {
                    AgentProgressEvent.Phase.ITERATION_START,
                    AgentProgressEvent.Phase.TOOL_CALL,
                    AgentProgressEvent.Phase.TOOL_RESULT,
                    AgentProgressEvent.Phase.ITERATION_END,
                    AgentProgressEvent.Phase.AGENT_DONE
                },
                phases);
    }

    @Test
    void agentProgressEvent_toKairoEvent_wrapsCorrectly() {
        AgentProgressEvent event =
                new AgentProgressEvent(
                        "my-agent", AgentProgressEvent.Phase.TOOL_CALL, 2, "read", 100L, 0, 0);

        KairoEvent kairoEvent = event.toKairoEvent();

        assertEquals(AgentProgressEvent.DOMAIN_AGENT, kairoEvent.domain());
        assertEquals("TOOL_CALL", kairoEvent.eventType());
        assertEquals(event, kairoEvent.payload());
    }

    // ---- DefaultReActAgent with eventBus tests ----

    @Test
    void agentWithEventBus_publishesIterationStartEvent() {
        TestableEventBus eventBus = new TestableEventBus();
        AgentConfig config = baseConfig().build();

        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(textResponse("final answer")));

        DefaultReActAgent agent =
                new DefaultReActAgent(config, toolExecutor, hookChain, null, null);
        agent.withEventBus(eventBus);

        Msg input = Msg.of(MsgRole.USER, "hello");
        agent.call(input).block(Duration.ofSeconds(10));

        // Should have at least one ITERATION_START event
        boolean foundIterationStart =
                eventBus.capturedEvents().stream()
                        .filter(e -> e.payload() instanceof AgentProgressEvent)
                        .map(e -> (AgentProgressEvent) e.payload())
                        .anyMatch(e -> e.phase() == AgentProgressEvent.Phase.ITERATION_START);
        assertTrue(foundIterationStart, "Expected at least one ITERATION_START event");
    }

    @Test
    void agentWithNullEventBus_doesNotThrowNPE() {
        AgentConfig config = baseConfig().build();

        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(textResponse("final answer")));

        DefaultReActAgent agent =
                new DefaultReActAgent(config, toolExecutor, hookChain, null, null);
        // Not setting eventBus — should be null by default

        Msg input = Msg.of(MsgRole.USER, "hello");
        StepVerifier.create(agent.call(input))
                .expectNextMatches(result -> result.text().contains("final answer"))
                .verifyComplete();
        // No NPE — test passes
    }

    @Test
    void agentProgressEvent_elapsedMsIsNonNegative() {
        AgentProgressEvent event =
                new AgentProgressEvent(
                        "my-agent", AgentProgressEvent.Phase.ITERATION_END, 1, "done", 0L, 0, 0);

        assertTrue(event.elapsedMs() >= 0);
    }

    // ---- AgentBuilder eventBus test ----

    @Test
    void agentBuilder_eventBus_wiresIntoAgent() {
        TestableEventBus eventBus = new TestableEventBus();

        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(textResponse("builder answer")));

        var agent =
                (DefaultReActAgent)
                        AgentBuilder.create()
                                .name("builder-agent")
                                .model(modelProvider)
                                .tools(toolRegistry)
                                .toolExecutor(toolExecutor)
                                .modelName("test-model")
                                .systemPrompt("You are a test agent.")
                                .maxIterations(5)
                                .eventBus(eventBus)
                                .build();

        Msg input = Msg.of(MsgRole.USER, "hello");
        agent.call(input).block(Duration.ofSeconds(10));

        // Should have captured events
        assertFalse(eventBus.capturedEvents().isEmpty(), "Expected events to be published");
    }

    // ---- Helper: in-memory event bus for testing ----

    static class TestableEventBus implements KairoEventBus {
        private final Sinks.Many<KairoEvent> sink =
                Sinks.many().multicast().onBackpressureBuffer(1024, false);
        private final List<KairoEvent> captured = new CopyOnWriteArrayList<>();

        @Override
        public void publish(KairoEvent event) {
            captured.add(event);
            sink.tryEmitNext(event);
        }

        @Override
        public Flux<KairoEvent> subscribe() {
            return sink.asFlux();
        }

        @Override
        public Flux<KairoEvent> subscribe(String domain) {
            return sink.asFlux().filter(e -> domain.equals(e.domain()));
        }

        List<KairoEvent> capturedEvents() {
            return captured;
        }
    }
}
