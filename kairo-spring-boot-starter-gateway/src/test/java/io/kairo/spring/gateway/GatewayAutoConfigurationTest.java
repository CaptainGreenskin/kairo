/*
 * Copyright 2025-2026 the Kairo authors.
 */
package io.kairo.spring.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.gateway.Channel;
import io.kairo.api.gateway.ChannelMessage;
import io.kairo.api.gateway.DeliveryTarget;
import io.kairo.api.gateway.Gateway;
import io.kairo.api.gateway.PlatformCapabilities;
import io.kairo.api.gateway.SendResult;
import io.kairo.gateway.session.SessionDirectory;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class GatewayAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(GatewayAutoConfiguration.class));

    @Test
    void gatewayBeanWiredWithZeroAdapters() {
        runner.run(
                ctx -> {
                    assertThat(ctx).hasSingleBean(Gateway.class);
                    Gateway gw = ctx.getBean(Gateway.class);
                    assertThat(gw.adapters()).isEmpty();
                });
    }

    @Test
    void registeredAdapterIsPickedUp() {
        runner.withUserConfiguration(SingleAdapterConfig.class)
                .run(
                        ctx -> {
                            Gateway gw = ctx.getBean(Gateway.class);
                            assertThat(gw.adapters()).hasSize(1);
                            assertThat(gw.adapter("loopback")).isPresent();
                        });
    }

    @Test
    void disabledViaProperty() {
        runner.withPropertyValues("kairo.gateway.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(Gateway.class));
    }

    @Test
    void sessionDirectoryBeanIsProvided() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(SessionDirectory.class));
    }

    @Configuration
    static class SingleAdapterConfig {
        @Bean
        Channel loopback() {
            return new LoopbackAdapter();
        }
    }

    static final class LoopbackAdapter implements Channel {
        private final Sinks.Many<ChannelMessage> inbound =
                Sinks.many().multicast().onBackpressureBuffer();

        @Override
        public String id() {
            return "loopback";
        }

        @Override
        public PlatformCapabilities capabilities() {
            return PlatformCapabilities.textOnly();
        }

        @Override
        public Mono<Void> connect() {
            return Mono.empty();
        }

        @Override
        public Mono<Void> disconnect() {
            return Mono.empty();
        }

        @Override
        public Flux<ChannelMessage> inbound() {
            return inbound.asFlux();
        }

        @Override
        public Mono<SendResult> send(
                DeliveryTarget target,
                String content,
                String replyToMessageId,
                Map<String, Object> metadata) {
            return Mono.just(SendResult.ok("loop"));
        }
    }
}
