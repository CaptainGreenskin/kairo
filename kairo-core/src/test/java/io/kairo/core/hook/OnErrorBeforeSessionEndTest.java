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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kairo.api.agent.Agent;
import io.kairo.api.hook.OnError;
import io.kairo.api.hook.OnSessionEnd;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.core.agent.AgentBuilder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class OnErrorBeforeSessionEndTest {

    static class OrderCapturingHandler {
        final List<String> events = new ArrayList<>();

        @OnError
        public AgentErrorEvent onError(AgentErrorEvent event) {
            events.add("OnError");
            return event;
        }

        @OnSessionEnd
        public Object onSessionEnd(Object event) {
            events.add("OnSessionEnd");
            return event;
        }
    }

    private Agent buildFailingAgent(Object hookHandler) {
        ModelProvider failingProvider = mock(ModelProvider.class);
        when(failingProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.error(new RuntimeException("simulated model failure")));

        return AgentBuilder.create()
                .name("failing-agent")
                .model(failingProvider)
                .modelName("mock-model")
                .maxIterations(1)
                .hook(hookHandler)
                .build();
    }

    @Test
    void onErrorFiredBeforeSessionEnd() {
        OrderCapturingHandler handler = new OrderCapturingHandler();
        Agent agent = buildFailingAgent(handler);

        StepVerifier.create(agent.call(Msg.of(MsgRole.USER, "hello")))
                .expectError(RuntimeException.class)
                .verify();

        assertThat(handler.events).contains("OnError");
        assertThat(handler.events).contains("OnSessionEnd");

        int onErrorIndex = handler.events.indexOf("OnError");
        int sessionEndIndex = handler.events.indexOf("OnSessionEnd");
        assertThat(onErrorIndex)
                .as("OnError must fire before OnSessionEnd")
                .isLessThan(sessionEndIndex);
    }

    @Test
    void bothHooksFiredOnFailure() {
        OrderCapturingHandler handler = new OrderCapturingHandler();
        Agent agent = buildFailingAgent(handler);

        StepVerifier.create(agent.call(Msg.of(MsgRole.USER, "hello")))
                .expectError(RuntimeException.class)
                .verify();

        assertThat(handler.events).containsOnlyOnce("OnError");
        assertThat(handler.events).containsOnlyOnce("OnSessionEnd");
    }

    @Test
    void originalExceptionPropagated() {
        OrderCapturingHandler handler = new OrderCapturingHandler();
        Agent agent = buildFailingAgent(handler);

        StepVerifier.create(agent.call(Msg.of(MsgRole.USER, "hello")))
                .expectErrorMatches(e -> e.getMessage().contains("simulated model failure"))
                .verify();
    }

    @Test
    void onErrorEventContainsAgentName() {
        List<AgentErrorEvent> captured = new ArrayList<>();

        Object capturingHandler =
                new Object() {
                    @OnError
                    public AgentErrorEvent onError(AgentErrorEvent event) {
                        captured.add(event);
                        return event;
                    }
                };

        Agent agent = buildFailingAgent(capturingHandler);

        StepVerifier.create(agent.call(Msg.of(MsgRole.USER, "hello")))
                .expectError(RuntimeException.class)
                .verify();

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).agentName()).isEqualTo("failing-agent");
        assertThat(captured.get(0).cause()).hasMessageContaining("simulated model failure");
    }
}
