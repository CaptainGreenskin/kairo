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
package io.kairo.core.health;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.core.agent.DefaultReActAgent;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.tool.DefaultPermissionGuard;
import io.kairo.core.tool.DefaultToolExecutor;
import io.kairo.core.tool.DefaultToolRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AgentCallObserverTest {

    @AfterEach
    void tearDown() {
        AgentCallObserver.setGlobal(NoopAgentCallObserver.INSTANCE);
    }

    // --- Global observer tests ---

    @Test
    void defaultGlobalInstanceIsNoop() {
        assertSame(NoopAgentCallObserver.INSTANCE, AgentCallObserver.global());
    }

    @Test
    void setGlobalSwapsInstance() {
        AgentCallObserver custom = mock(AgentCallObserver.class);
        AgentCallObserver.setGlobal(custom);
        assertSame(custom, AgentCallObserver.global());
    }

    @Test
    void globalReflectsLatestSetGlobal() {
        AgentCallObserver first = mock(AgentCallObserver.class);
        AgentCallObserver second = mock(AgentCallObserver.class);
        AgentCallObserver.setGlobal(first);
        assertSame(first, AgentCallObserver.global());
        AgentCallObserver.setGlobal(second);
        assertSame(second, AgentCallObserver.global());
    }

    @Test
    void concurrentSetGlobalIsThreadSafe() throws InterruptedException {
        int threads = 50;
        ExecutorService exec = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicBoolean allNonNull = new AtomicBoolean(true);

        for (int i = 0; i < threads; i++) {
            final AgentCallObserver observer = mock(AgentCallObserver.class);
            exec.submit(
                    () -> {
                        try {
                            start.await();
                            AgentCallObserver.setGlobal(observer);
                            if (AgentCallObserver.global() == null) {
                                allNonNull.set(false);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
        }
        start.countDown();
        done.await();
        exec.shutdown();

        assertTrue(allNonNull.get(), "global() should never be null under concurrent setGlobal");
        assertNotNull(AgentCallObserver.global());
    }

    // --- DefaultReActAgent integration tests ---

    private ModelProvider modelProvider;
    private DefaultToolRegistry toolRegistry;
    private DefaultToolExecutor toolExecutor;

    @BeforeEach
    void setUpAgent() {
        modelProvider = mock(ModelProvider.class);
        toolRegistry = new DefaultToolRegistry();
        toolExecutor = new DefaultToolExecutor(toolRegistry, new DefaultPermissionGuard());
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

    private DefaultReActAgent createAgent(AgentConfig config) {
        return new DefaultReActAgent(
                config,
                toolExecutor,
                new DefaultHookChain(),
                null,
                (io.kairo.api.guardrail.GuardrailChain) null);
    }

    private ModelResponse textResponse(String text) {
        return new ModelResponse(
                "resp-1",
                List.of(new io.kairo.api.message.Content.TextContent(text)),
                new ModelResponse.Usage(10, 20, 0, 0),
                ModelResponse.StopReason.END_TURN,
                "test-model");
    }

    @Test
    void onCallStartInvokedOnAgentCall() {
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(textResponse("Hello")));

        AgentCallObserver observer = mock(AgentCallObserver.class);
        AgentCallObserver.setGlobal(observer);

        DefaultReActAgent agent = createAgent(baseConfig().build());
        StepVerifier.create(agent.call(Msg.of(MsgRole.USER, "Hi")))
                .assertNext(msg -> assertNotNull(msg))
                .verifyComplete();

        verify(observer, times(1)).onCallStart(eq(agent.id()), eq("test-agent"));
    }

    @Test
    void onCallEndSuccessInvokedOnAgentCompletion() {
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(textResponse("Done")));

        AgentCallObserver observer = mock(AgentCallObserver.class);
        AgentCallObserver.setGlobal(observer);

        DefaultReActAgent agent = createAgent(baseConfig().build());
        StepVerifier.create(agent.call(Msg.of(MsgRole.USER, "Hi")))
                .assertNext(msg -> assertNotNull(msg))
                .verifyComplete();

        verify(observer, times(1))
                .onCallEnd(eq(agent.id()), eq("test-agent"), any(Duration.class), eq(true));
    }

    @Test
    void onCallEndFailureInvokedOnAgentError() {
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.error(new RuntimeException("model failure")));

        AgentCallObserver observer = mock(AgentCallObserver.class);
        AgentCallObserver.setGlobal(observer);

        DefaultReActAgent agent = createAgent(baseConfig().build());
        StepVerifier.create(agent.call(Msg.of(MsgRole.USER, "Hi")))
                .expectError(RuntimeException.class)
                .verify();

        verify(observer, times(1))
                .onCallEnd(eq(agent.id()), eq("test-agent"), any(Duration.class), eq(false));
    }

    @Test
    void onCallEndReceivesPositiveDuration() {
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(textResponse("OK")));

        AtomicInteger durationMs = new AtomicInteger(-1);

        AgentCallObserver observer =
                new AgentCallObserver() {
                    @Override
                    public void onCallStart(String agentId, String agentName) {}

                    @Override
                    public void onCallEnd(
                            String agentId, String agentName, Duration duration, boolean success) {
                        durationMs.set((int) duration.toMillis());
                    }
                };
        AgentCallObserver.setGlobal(observer);

        DefaultReActAgent agent = createAgent(baseConfig().build());
        StepVerifier.create(agent.call(Msg.of(MsgRole.USER, "Hi")))
                .assertNext(msg -> assertNotNull(msg))
                .verifyComplete();

        assertTrue(durationMs.get() >= 0, "duration should be non-negative");
    }
}
