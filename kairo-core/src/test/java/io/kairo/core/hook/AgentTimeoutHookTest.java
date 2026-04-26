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
package io.kairo.core.hook;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.hook.OnError;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class AgentTimeoutHookTest {

    private DefaultHookChain chain;

    @BeforeEach
    void setUp() {
        chain = new DefaultHookChain();
    }

    static class CapturingErrorHandler {
        final List<AgentErrorEvent> captured = new ArrayList<>();

        @OnError
        public AgentErrorEvent onError(AgentErrorEvent event) {
            captured.add(event);
            return event;
        }
    }

    @Test
    void onErrorHookFiredOnTimeout() {
        CapturingErrorHandler handler = new CapturingErrorHandler();
        chain.register(handler);

        RuntimeException timeout =
                new io.kairo.api.exception.AgentInterruptedException("timed out after 30s");
        AgentErrorEvent event = AgentErrorEvent.of("my-agent", timeout);

        StepVerifier.create(chain.fireOnError(event)).verifyComplete();

        assertThat(handler.captured).hasSize(1);
        assertThat(handler.captured.get(0).agentName()).isEqualTo("my-agent");
    }

    @Test
    void onErrorEventCarriesErrorType() {
        AtomicReference<AgentErrorEvent> ref = new AtomicReference<>();
        chain.register(
                new Object() {
                    @OnError
                    public AgentErrorEvent handle(AgentErrorEvent e) {
                        ref.set(e);
                        return e;
                    }
                });

        RuntimeException cause = new IllegalStateException("something went wrong");
        AgentErrorEvent event = AgentErrorEvent.of("agent-x", cause);

        chain.fireOnError(event).block();

        assertThat(ref.get()).isNotNull();
        assertThat(ref.get().errorType()).isEqualTo("IllegalStateException");
        assertThat(ref.get().cause()).isSameAs(cause);
    }

    @Test
    void onErrorHookFiredForNonTimeoutError() {
        CapturingErrorHandler handler = new CapturingErrorHandler();
        chain.register(handler);

        Throwable oom = new OutOfMemoryError("heap exhausted");
        AgentErrorEvent event = AgentErrorEvent.of("oom-agent", oom);

        StepVerifier.create(chain.fireOnError(event)).verifyComplete();

        assertThat(handler.captured).hasSize(1);
        assertThat(handler.captured.get(0).errorType()).isEqualTo("OutOfMemoryError");
    }

    @Test
    void noHandlersRegistered_completes() {
        AgentErrorEvent event = AgentErrorEvent.of("silent-agent", new RuntimeException("fail"));

        StepVerifier.create(chain.fireOnError(event)).verifyComplete();
    }

    @Test
    void handlerThrowing_propagatesError() {
        chain.register(
                new Object() {
                    @OnError
                    public AgentErrorEvent handle(AgentErrorEvent e) {
                        throw new RuntimeException("hook internal failure");
                    }
                });

        AgentErrorEvent event = AgentErrorEvent.of("agent", new RuntimeException("original"));

        StepVerifier.create(chain.fireOnError(event))
                .expectErrorMessage("hook internal failure")
                .verify();
    }
}
