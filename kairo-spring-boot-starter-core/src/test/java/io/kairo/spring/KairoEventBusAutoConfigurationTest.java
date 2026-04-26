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
package io.kairo.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.KairoEventBus;
import io.kairo.api.execution.DurableExecution;
import io.kairo.api.execution.DurableExecutionStore;
import io.kairo.api.execution.ExecutionStatus;
import io.kairo.api.guardrail.GuardrailPhase;
import io.kairo.api.guardrail.SecurityEvent;
import io.kairo.api.guardrail.SecurityEventSink;
import io.kairo.api.guardrail.SecurityEventType;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.core.event.BusBridgingSecurityEventSink;
import io.kairo.core.event.DefaultKairoEventBus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Verifies the v0.10.2 B1.1 default wiring for the {@link KairoEventBus}:
 *
 * <ul>
 *   <li>The starter registers a default {@link DefaultKairoEventBus} bean.
 *   <li>The default {@link SecurityEventSink} is wrapped with {@link BusBridgingSecurityEventSink},
 *       so guardrail decisions are observable on the bus.
 *   <li>A user-provided {@link SecurityEventSink} replaces the default without being wrapped.
 *   <li>{@link DurableExecutionStore} publishes lifecycle transitions on {@link
 *       KairoEvent#DOMAIN_EXECUTION}.
 * </ul>
 */
class KairoEventBusAutoConfigurationTest {

    private static final ModelProvider NOOP_PROVIDER =
            new ModelProvider() {
                @Override
                public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
                    return Mono.empty();
                }

                @Override
                public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
                    return Flux.empty();
                }

                @Override
                public String name() {
                    return "noop";
                }
            };

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(AgentRuntimeAutoConfiguration.class))
                    .withBean("modelProvider", ModelProvider.class, () -> NOOP_PROVIDER);

    @Test
    void defaultEventBusBeanIsRegistered() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(KairoEventBus.class);
                    assertThat(context.getBean(KairoEventBus.class))
                            .isInstanceOf(DefaultKairoEventBus.class);
                });
    }

    @Test
    void defaultSecurityEventSinkBridgesToBus() {
        runner.run(
                context -> {
                    SecurityEventSink sink = context.getBean(SecurityEventSink.class);
                    assertThat(sink).isInstanceOf(BusBridgingSecurityEventSink.class);

                    KairoEventBus bus = context.getBean(KairoEventBus.class);
                    CopyOnWriteArrayList<KairoEvent> received = new CopyOnWriteArrayList<>();
                    bus.subscribe(KairoEvent.DOMAIN_SECURITY).subscribe(received::add);

                    sink.record(
                            new SecurityEvent(
                                    Instant.now(),
                                    SecurityEventType.GUARDRAIL_DENY,
                                    "test-agent",
                                    "test-tool",
                                    GuardrailPhase.PRE_TOOL,
                                    "test-policy",
                                    "blocked by policy",
                                    Map.of()));

                    assertThat(received).hasSize(1);
                    assertThat(received.get(0).domain()).isEqualTo(KairoEvent.DOMAIN_SECURITY);
                    assertThat(received.get(0).eventType()).isEqualTo("GUARDRAIL_DENY");
                    assertThat(received.get(0).attributes())
                            .containsEntry("agentName", "test-agent")
                            .containsEntry("targetName", "test-tool")
                            .containsEntry("policyName", "test-policy");
                });
    }

    @Test
    void userProvidedSecurityEventSinkReplacesDefaultWithoutWrapping() {
        CapturingSink userSink = new CapturingSink();
        runner.withBean("securityEventSink", SecurityEventSink.class, () -> userSink)
                .run(
                        context -> {
                            SecurityEventSink sink = context.getBean(SecurityEventSink.class);
                            assertThat(sink).isSameAs(userSink);
                            assertThat(sink).isNotInstanceOf(BusBridgingSecurityEventSink.class);
                            // bus bean is still registered even though the sink is user-supplied
                            assertThat(context).hasSingleBean(KairoEventBus.class);
                        });
    }

    @Test
    void userProvidedEventBusReplacesDefault() {
        RecordingBus recordingBus = new RecordingBus();
        runner.withBean("kairoEventBus", KairoEventBus.class, () -> recordingBus)
                .run(
                        context -> {
                            KairoEventBus bus = context.getBean(KairoEventBus.class);
                            assertThat(bus).isSameAs(recordingBus);

                            SecurityEventSink sink = context.getBean(SecurityEventSink.class);
                            sink.record(
                                    new SecurityEvent(
                                            Instant.now(),
                                            SecurityEventType.GUARDRAIL_ALLOW,
                                            "agent",
                                            "tool",
                                            GuardrailPhase.PRE_TOOL,
                                            "policy",
                                            "ok",
                                            Map.of()));
                            assertThat(recordingBus.events).hasSize(1);
                            assertThat(recordingBus.events.get(0).domain())
                                    .isEqualTo(KairoEvent.DOMAIN_SECURITY);
                        });
    }

    @Test
    void durableExecutionStorePublishesLifecycleEvents() {
        runner.withPropertyValues(
                        "kairo.execution.durable.enabled=true",
                        "kairo.execution.durable.store-type=memory")
                .run(
                        context -> {
                            DurableExecutionStore store =
                                    context.getBean(DurableExecutionStore.class);
                            KairoEventBus bus = context.getBean(KairoEventBus.class);

                            CopyOnWriteArrayList<KairoEvent> received =
                                    new CopyOnWriteArrayList<>();
                            bus.subscribe(KairoEvent.DOMAIN_EXECUTION).subscribe(received::add);

                            DurableExecution execution =
                                    new DurableExecution(
                                            "exec-1",
                                            "agent-1",
                                            List.of(),
                                            null,
                                            ExecutionStatus.RUNNING,
                                            0,
                                            Instant.now(),
                                            Instant.now());
                            store.persist(execution).block(Duration.ofSeconds(2));
                            store.updateStatus("exec-1", ExecutionStatus.COMPLETED, 0)
                                    .block(Duration.ofSeconds(2));

                            assertThat(received).hasSizeGreaterThanOrEqualTo(2);
                            assertThat(received)
                                    .extracting(KairoEvent::eventType)
                                    .contains("EXECUTION_PERSISTED", "EXECUTION_STATUS_CHANGED");
                            assertThat(received.get(0).attributes())
                                    .containsEntry("executionId", "exec-1")
                                    .containsEntry("agentId", "agent-1");
                        });
    }

    private static final class CapturingSink implements SecurityEventSink {
        final CopyOnWriteArrayList<SecurityEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void record(SecurityEvent event) {
            events.add(event);
        }
    }

    private static final class RecordingBus implements KairoEventBus {
        final CopyOnWriteArrayList<KairoEvent> events = new CopyOnWriteArrayList<>();
        private final DefaultKairoEventBus delegate = new DefaultKairoEventBus();

        @Override
        public void publish(KairoEvent event) {
            events.add(event);
            delegate.publish(event);
        }

        @Override
        public Flux<KairoEvent> subscribe() {
            return delegate.subscribe();
        }

        @Override
        public Flux<KairoEvent> subscribe(String domain) {
            return delegate.subscribe(domain);
        }
    }
}
