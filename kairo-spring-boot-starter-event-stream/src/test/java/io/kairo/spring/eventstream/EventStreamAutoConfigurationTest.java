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
package io.kairo.spring.eventstream;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.KairoEventBus;
import io.kairo.api.event.stream.BackpressurePolicy;
import io.kairo.api.event.stream.KairoEventStreamAuthorizer;
import io.kairo.api.event.stream.KairoEventStreamAuthorizer.AuthorizationDecision;
import io.kairo.eventstream.EventStreamRegistry;
import io.kairo.eventstream.EventStreamService;
import io.kairo.eventstream.sse.KairoEventStreamSseHandler;
import io.kairo.eventstream.ws.KairoEventStreamWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import reactor.core.publisher.Flux;

class EventStreamAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    JacksonAutoConfiguration.class,
                                    EventStreamAutoConfiguration.class));

    @Test
    void defaultOff_noBeansRegistered() {
        contextRunner
                .withUserConfiguration(BusAndAuthorizerConfig.class)
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(EventStreamAutoConfiguration.class);
                            assertThat(context).doesNotHaveBean(EventStreamService.class);
                            assertThat(context).doesNotHaveBean(KairoEventStreamSseHandler.class);
                            assertThat(context)
                                    .doesNotHaveBean(KairoEventStreamWebSocketHandler.class);
                        });
    }

    @Test
    void enabledWithoutAuthorizer_serviceAndTransportsAbsent_denySafe() {
        contextRunner
                .withPropertyValues("kairo.event-stream.enabled=true")
                .withUserConfiguration(BusOnlyConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(EventStreamAutoConfiguration.class);
                            assertThat(context).hasSingleBean(EventStreamRegistry.class);
                            assertThat(context).doesNotHaveBean(EventStreamService.class);
                            assertThat(context).doesNotHaveBean(KairoEventStreamSseHandler.class);
                            assertThat(context)
                                    .doesNotHaveBean(KairoEventStreamWebSocketHandler.class);
                        });
    }

    @Test
    void enabledWithAuthorizer_wiresServiceAndBothTransports() {
        contextRunner
                .withPropertyValues("kairo.event-stream.enabled=true")
                .withUserConfiguration(BusAndAuthorizerConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(EventStreamService.class);
                            assertThat(context).hasSingleBean(KairoEventStreamSseHandler.class);
                            assertThat(context).hasSingleBean(KairoEventStreamSseController.class);
                            assertThat(context)
                                    .hasSingleBean(KairoEventStreamWebSocketHandler.class);
                            assertThat(context.getBeansOfType(HandlerMapping.class))
                                    .containsKey("kairoEventStreamWebSocketHandlerMapping");
                        });
    }

    @Test
    void sseDisabled_onlyWebSocketWired() {
        contextRunner
                .withPropertyValues(
                        "kairo.event-stream.enabled=true", "kairo.event-stream.sse.enabled=false")
                .withUserConfiguration(BusAndAuthorizerConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(EventStreamService.class);
                            assertThat(context).doesNotHaveBean(KairoEventStreamSseHandler.class);
                            assertThat(context)
                                    .doesNotHaveBean(KairoEventStreamSseController.class);
                            assertThat(context)
                                    .hasSingleBean(KairoEventStreamWebSocketHandler.class);
                        });
    }

    @Test
    void wsDisabled_onlySseWired() {
        contextRunner
                .withPropertyValues(
                        "kairo.event-stream.enabled=true", "kairo.event-stream.ws.enabled=false")
                .withUserConfiguration(BusAndAuthorizerConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(EventStreamService.class);
                            assertThat(context).hasSingleBean(KairoEventStreamSseHandler.class);
                            assertThat(context)
                                    .doesNotHaveBean(KairoEventStreamWebSocketHandler.class);
                            assertThat(context.getBeansOfType(HandlerMapping.class))
                                    .doesNotContainKey("kairoEventStreamWebSocketHandlerMapping");
                        });
    }

    @Test
    void propertiesBindWithCustomValues() {
        contextRunner
                .withPropertyValues(
                        "kairo.event-stream.enabled=true",
                        "kairo.event-stream.default-buffer-capacity=64",
                        "kairo.event-stream.default-policy=ERROR_ON_OVERFLOW",
                        "kairo.event-stream.sse.path=/custom/sse",
                        "kairo.event-stream.ws.path=/custom/ws")
                .withUserConfiguration(BusAndAuthorizerConfig.class)
                .run(
                        context -> {
                            EventStreamProperties props =
                                    context.getBean(EventStreamProperties.class);
                            assertThat(props.isEnabled()).isTrue();
                            assertThat(props.getDefaultBufferCapacity()).isEqualTo(64);
                            assertThat(props.getDefaultPolicy())
                                    .isEqualTo(BackpressurePolicy.ERROR_ON_OVERFLOW);
                            assertThat(props.getSse().getPath()).isEqualTo("/custom/sse");
                            assertThat(props.getWs().getPath()).isEqualTo("/custom/ws");
                        });
    }

    @Test
    void userProvidedEventStreamServiceWins() {
        EventStreamService userService =
                new EventStreamService() {
                    @Override
                    public io.kairo.api.event.stream.EventStreamSubscription subscribe(
                            io.kairo.api.event.stream.EventStreamSubscriptionRequest request) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public int activeSubscriptionCount() {
                        return 42;
                    }
                };
        contextRunner
                .withPropertyValues("kairo.event-stream.enabled=true")
                .withUserConfiguration(BusAndAuthorizerConfig.class)
                .withBean("userService", EventStreamService.class, () -> userService)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(EventStreamService.class);
                            assertThat(context.getBean(EventStreamService.class))
                                    .isSameAs(userService);
                        });
    }

    @Configuration(proxyBeanMethods = false)
    static class BusOnlyConfig {
        @Bean
        KairoEventBus kairoEventBus() {
            return new TestEventBus();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class BusAndAuthorizerConfig {
        @Bean
        KairoEventBus kairoEventBus() {
            return new TestEventBus();
        }

        @Bean
        KairoEventStreamAuthorizer kairoEventStreamAuthorizer() {
            return req -> AuthorizationDecision.allow();
        }
    }

    /** Minimal {@link KairoEventBus} that emits no events and ignores publishes. */
    static final class TestEventBus implements KairoEventBus {
        @Override
        public void publish(KairoEvent event) {
            // no-op
        }

        @Override
        public Flux<KairoEvent> subscribe() {
            return Flux.empty();
        }

        @Override
        public Flux<KairoEvent> subscribe(String domain) {
            return Flux.empty();
        }
    }
}
