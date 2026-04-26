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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.context.ContextManager;
import io.kairo.api.hook.PostCompact;
import io.kairo.api.hook.PostCompactEvent;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.core.context.TokenBudgetManager;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.model.ModelFallbackManager;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/** Tests that PostCompact hook is fired with metrics after compaction. */
class CompactionMetricsHookTest {

    private ContextManager contextManager;
    private ReActLoop reactLoop;
    private DefaultHookChain hookChain;

    @BeforeEach
    void setUp() {
        contextManager = mock(ContextManager.class);
        hookChain = new DefaultHookChain();

        ModelProvider modelProvider = mock(ModelProvider.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        AgentConfig config =
                AgentConfig.builder()
                        .name("test-agent")
                        .modelProvider(modelProvider)
                        .modelName("test-model")
                        .maxIterations(10)
                        .tokenBudget(200_000)
                        .build();
        ErrorRecoveryStrategy errorRecovery =
                new ErrorRecoveryStrategy(modelProvider, null, new ModelFallbackManager(List.of()));
        ReActLoopContext ctx =
                new ReActLoopContext(
                        "agent-1",
                        "test-agent",
                        config,
                        hookChain,
                        null,
                        toolExecutor,
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
        reactLoop =
                new ReActLoop(
                        ctx,
                        new AtomicBoolean(false),
                        new AtomicInteger(0),
                        new AtomicLong(0),
                        () -> modelConfig);
    }

    private Msg msg(String text) {
        return Msg.builder().role(MsgRole.USER).addContent(new Content.TextContent(text)).build();
    }

    @Test
    @DisplayName("PostCompact hook is fired after compaction")
    void postCompactHookFiredAfterCompaction() {
        List<Msg> compactedHistory = List.of(msg("summary"));
        when(contextManager.needsCompaction(anyList())).thenReturn(true);
        when(contextManager.compactMessages(anyList())).thenReturn(Mono.just(compactedHistory));

        List<PostCompactEvent> received = new ArrayList<>();
        hookChain.register(
                new Object() {
                    @PostCompact
                    public PostCompactEvent onPostCompact(PostCompactEvent event) {
                        received.add(event);
                        return event;
                    }
                });

        List<Msg> history = List.of(msg("m1"), msg("m2"), msg("m3"));
        CompactionTrigger trigger =
                new CompactionTrigger(contextManager, reactLoop, null, null, hookChain);

        StepVerifier.create(trigger.checkAndCompact(history)).expectNext(true).verifyComplete();

        assertThat(received).hasSize(1);
    }

    @Test
    @DisplayName("PostCompact event contains messagesSaved as tokensSaved metric")
    void postCompactEventContainsMetrics() {
        List<Msg> compactedHistory = List.of(msg("summary"));
        when(contextManager.needsCompaction(anyList())).thenReturn(true);
        when(contextManager.compactMessages(anyList())).thenReturn(Mono.just(compactedHistory));

        List<PostCompactEvent> received = new ArrayList<>();
        hookChain.register(
                new Object() {
                    @PostCompact
                    public PostCompactEvent onPostCompact(PostCompactEvent event) {
                        received.add(event);
                        return event;
                    }
                });

        // 4 messages before, 1 after → 3 saved
        List<Msg> history = List.of(msg("m1"), msg("m2"), msg("m3"), msg("m4"));
        CompactionTrigger trigger =
                new CompactionTrigger(contextManager, reactLoop, null, null, hookChain);

        StepVerifier.create(trigger.checkAndCompact(history)).expectNext(true).verifyComplete();

        assertThat(received).hasSize(1);
        PostCompactEvent event = received.get(0);
        assertThat(event.tokensSaved()).isEqualTo(3);
        assertThat(event.compactedMessages()).hasSize(1);
    }

    @Test
    @DisplayName("PostCompact hook not fired when hookChain is null")
    void postCompactHookNotFiredWithoutHookChain() {
        List<Msg> compactedHistory = List.of(msg("summary"));
        when(contextManager.needsCompaction(anyList())).thenReturn(true);
        when(contextManager.compactMessages(anyList())).thenReturn(Mono.just(compactedHistory));

        List<Msg> history = List.of(msg("m1"), msg("m2"), msg("m3"));
        // hookChain is null — no hook should be invoked
        CompactionTrigger trigger = new CompactionTrigger(contextManager, reactLoop, null, null);

        StepVerifier.create(trigger.checkAndCompact(history)).expectNext(true).verifyComplete();
        // no assertion needed — verifies no NullPointerException
    }

    @Test
    @DisplayName("PostCompact hook failure does not abort compaction")
    void postCompactHookFailureIsBestEffort() {
        List<Msg> compactedHistory = List.of(msg("summary"));
        when(contextManager.needsCompaction(anyList())).thenReturn(true);
        when(contextManager.compactMessages(anyList())).thenReturn(Mono.just(compactedHistory));

        hookChain.register(
                new Object() {
                    @PostCompact
                    public PostCompactEvent onPostCompact(PostCompactEvent event) {
                        throw new RuntimeException("hook failure");
                    }
                });

        List<Msg> history = List.of(msg("m1"), msg("m2"));
        CompactionTrigger trigger =
                new CompactionTrigger(contextManager, reactLoop, null, null, hookChain);

        // Compaction should still return true even when hook throws
        StepVerifier.create(trigger.checkAndCompact(history)).expectNext(true).verifyComplete();
    }
}
