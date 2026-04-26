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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.memory.MemoryEntry;
import io.kairo.api.memory.MemoryStore;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.core.context.TokenBudgetManager;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.model.ModelFallbackManager;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Unit tests for {@link SessionResumption}. */
class SessionResumptionTest {

    private ReActLoop createLoop(AgentConfig config) {
        ErrorRecoveryStrategy errorRecovery =
                new ErrorRecoveryStrategy(
                        mock(ModelProvider.class), null, new ModelFallbackManager(List.of()));

        ReActLoopContext ctx =
                new ReActLoopContext(
                        "agent-id",
                        "test-agent",
                        config,
                        new DefaultHookChain(),
                        null,
                        mock(ToolExecutor.class),
                        errorRecovery,
                        new TokenBudgetManager(200_000, 8_096),
                        new GracefulShutdownManager(),
                        null,
                        null);

        ModelConfig modelConfig =
                ModelConfig.builder()
                        .model("test-model")
                        .maxTokens(4096)
                        .temperature(0.7)
                        .tools(List.of())
                        .build();

        return new ReActLoop(
                ctx,
                new AtomicBoolean(false),
                new AtomicInteger(0),
                new AtomicLong(0),
                () -> modelConfig);
    }

    // ===== No sessionId =====

    @Test
    void noSessionId_doesNotInjectMessages() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("test-agent")
                        .modelProvider(mock(ModelProvider.class))
                        .modelName("test-model")
                        .maxIterations(10)
                        .tokenBudget(200_000)
                        .build();

        ReActLoop loop = createLoop(config);
        SessionResumption resumption = new SessionResumption(config, loop);

        StepVerifier.create(resumption.loadSessionIfConfigured()).verifyComplete();

        assertThat(loop.getHistory()).isEmpty();
    }

    // ===== No memoryStore =====

    @Test
    void noMemoryStore_doesNotInjectMessages() {
        AgentConfig config =
                AgentConfig.builder()
                        .name("test-agent")
                        .modelProvider(mock(ModelProvider.class))
                        .modelName("test-model")
                        .maxIterations(10)
                        .tokenBudget(200_000)
                        .sessionId("sess-123")
                        // no memoryStore
                        .build();

        ReActLoop loop = createLoop(config);
        SessionResumption resumption = new SessionResumption(config, loop);

        StepVerifier.create(resumption.loadSessionIfConfigured()).verifyComplete();

        assertThat(loop.getHistory()).isEmpty();
    }

    // ===== MemoryStore has no data for this session =====

    @Test
    void memoryStoreReturnsEmpty_doesNotInjectMessages() {
        MemoryStore memoryStore = mock(MemoryStore.class);
        when(memoryStore.get(eq("session-sess-456"))).thenReturn(Mono.empty());

        AgentConfig config =
                AgentConfig.builder()
                        .name("test-agent")
                        .modelProvider(mock(ModelProvider.class))
                        .modelName("test-model")
                        .maxIterations(10)
                        .tokenBudget(200_000)
                        .sessionId("sess-456")
                        .memoryStore(memoryStore)
                        .build();

        ReActLoop loop = createLoop(config);
        SessionResumption resumption = new SessionResumption(config, loop);

        StepVerifier.create(resumption.loadSessionIfConfigured()).verifyComplete();

        assertThat(loop.getHistory()).isEmpty();
    }

    // ===== MemoryStore returns session data → injects message =====

    @Test
    void memoryStoreReturnsSession_injectsContextMessage() {
        MemoryStore memoryStore = mock(MemoryStore.class);
        MemoryEntry entry =
                MemoryEntry.session("session-sess-789", "Previous session summary.", null);
        when(memoryStore.get(eq("session-sess-789"))).thenReturn(Mono.just(entry));

        AgentConfig config =
                AgentConfig.builder()
                        .name("test-agent")
                        .modelProvider(mock(ModelProvider.class))
                        .modelName("test-model")
                        .maxIterations(10)
                        .tokenBudget(200_000)
                        .sessionId("sess-789")
                        .memoryStore(memoryStore)
                        .build();

        ReActLoop loop = createLoop(config);
        SessionResumption resumption = new SessionResumption(config, loop);

        StepVerifier.create(resumption.loadSessionIfConfigured()).verifyComplete();

        assertThat(loop.getHistory()).hasSize(1);
        assertThat(loop.getHistory().get(0).text()).contains("Previous session summary.");
    }

    // ===== Resilience: MemoryStore error does not fail the Mono =====

    @Test
    void memoryStoreThrows_doesNotPropagateError() {
        MemoryStore memoryStore = mock(MemoryStore.class);
        when(memoryStore.get(eq("session-sess-err")))
                .thenReturn(Mono.error(new RuntimeException("store unavailable")));

        AgentConfig config =
                AgentConfig.builder()
                        .name("test-agent")
                        .modelProvider(mock(ModelProvider.class))
                        .modelName("test-model")
                        .maxIterations(10)
                        .tokenBudget(200_000)
                        .sessionId("sess-err")
                        .memoryStore(memoryStore)
                        .build();

        ReActLoop loop = createLoop(config);
        SessionResumption resumption = new SessionResumption(config, loop);

        // Error should be swallowed gracefully
        StepVerifier.create(resumption.loadSessionIfConfigured()).verifyComplete();

        assertThat(loop.getHistory()).isEmpty();
    }
}
