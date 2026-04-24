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
package io.kairo.spring.channel;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.channel.ChannelAck;
import io.kairo.api.channel.ChannelInboundHandler;
import io.kairo.channel.ChannelRegistry;
import io.kairo.channel.LoopbackChannel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

class ChannelAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ChannelAutoConfiguration.class));

    @Test
    void defaultOff_noChannelBeansWired() {
        contextRunner.run(
                context -> {
                    assertThat(context).doesNotHaveBean(ChannelRegistry.class);
                    assertThat(context).doesNotHaveBean(ChannelLifecycleManager.class);
                });
    }

    @Test
    void enabledWithoutHandler_onlyRegistryWired() {
        contextRunner
                .withPropertyValues("kairo.channel.enabled=true")
                .withUserConfiguration(ChannelsOnlyConfig.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(ChannelRegistry.class);
                            assertThat(context).doesNotHaveBean(ChannelLifecycleManager.class);
                            LoopbackChannel channel =
                                    context.getBean("loopback", LoopbackChannel.class);
                            assertThat(channel.isRunning()).isFalse();
                        });
    }

    @Test
    void enabledWithHandler_registersAndStartsChannels() {
        contextRunner
                .withPropertyValues("kairo.channel.enabled=true")
                .withUserConfiguration(ChannelsAndHandlerConfig.class)
                .run(
                        context -> {
                            ChannelRegistry registry = context.getBean(ChannelRegistry.class);
                            LoopbackChannel channel =
                                    context.getBean("loopback", LoopbackChannel.class);

                            assertThat(context).hasSingleBean(ChannelLifecycleManager.class);
                            assertThat(registry.get("loopback")).contains(channel);
                            assertThat(channel.isRunning()).isTrue();
                        });
    }

    @Test
    void autoStartFalse_registersButDoesNotStart() {
        contextRunner
                .withPropertyValues("kairo.channel.enabled=true", "kairo.channel.auto-start=false")
                .withUserConfiguration(ChannelsAndHandlerConfig.class)
                .run(
                        context -> {
                            LoopbackChannel channel =
                                    context.getBean("loopback", LoopbackChannel.class);
                            ChannelRegistry registry = context.getBean(ChannelRegistry.class);

                            assertThat(registry.get("loopback")).contains(channel);
                            assertThat(channel.isRunning()).isFalse();
                        });
    }

    @Test
    void userProvidedRegistryWins() {
        ChannelRegistry userRegistry = new ChannelRegistry();
        contextRunner
                .withPropertyValues("kairo.channel.enabled=true")
                .withBean(ChannelRegistry.class, () -> userRegistry)
                .run(
                        context ->
                                assertThat(context.getBean(ChannelRegistry.class))
                                        .isSameAs(userRegistry));
    }

    @Configuration(proxyBeanMethods = false)
    static class ChannelsOnlyConfig {
        @Bean
        LoopbackChannel loopback() {
            return new LoopbackChannel("loopback");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ChannelsAndHandlerConfig {
        @Bean
        LoopbackChannel loopback() {
            return new LoopbackChannel("loopback");
        }

        @Bean
        ChannelInboundHandler handler() {
            return message -> Mono.just(ChannelAck.ok());
        }
    }
}
