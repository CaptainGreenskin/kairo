/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.spring.gateway;

import io.kairo.api.gateway.Channel;
import io.kairo.api.gateway.Gateway;
import io.kairo.gateway.DefaultGateway;
import io.kairo.gateway.cmd.SlashCommandRegistry;
import io.kairo.gateway.hooks.GatewayHookRegistry;
import io.kairo.gateway.mirror.JsonlMirrorStore;
import io.kairo.gateway.mirror.MirrorStore;
import io.kairo.gateway.session.PairingStore;
import io.kairo.gateway.session.SessionDirectory;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures the Kairo gateway from Spring bean wiring. Every {@link Channel} bean discovered
 * in the application context is registered with a single {@link DefaultGateway}. {@link
 * SessionDirectory}, {@link PairingStore}, {@link SlashCommandRegistry}, and {@link
 * GatewayHookRegistry} get default beans that applications can override.
 *
 * <p>{@link MirrorStore} is JSONL when {@code kairo.gateway.mirror-path} is set, otherwise no-op.
 * Lifecycle: the gateway is started during bean init and stopped via {@code destroyMethod} on
 * context shutdown, mirroring the cron starter's pattern. Set {@code
 * kairo.gateway.auto-start=false} to skip the eager start (useful for tests that want to drive
 * lifecycle themselves).
 *
 * <p>When zero adapter beans are present, the gateway still constructs cleanly — the inbound flux
 * is empty. That keeps the starter useful in shaping applications even before any IM transport is
 * wired in.
 */
@AutoConfiguration
@ConditionalOnClass(DefaultGateway.class)
@ConditionalOnProperty(prefix = "kairo.gateway", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(KairoGatewayProperties.class)
public class GatewayAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GatewayAutoConfiguration.class);

    private final KairoGatewayProperties props;

    public GatewayAutoConfiguration(KairoGatewayProperties props) {
        this.props = props;
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionDirectory kairoGatewaySessionDirectory() {
        return new SessionDirectory();
    }

    @Bean
    @ConditionalOnMissingBean
    public PairingStore kairoGatewayPairingStore() {
        if (props.pairingPath() != null && !props.pairingPath().isBlank()) {
            return new PairingStore(Path.of(props.pairingPath()));
        }
        return new PairingStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public MirrorStore kairoGatewayMirrorStore() {
        if (props.mirrorPath() != null && !props.mirrorPath().isBlank()) {
            return new JsonlMirrorStore(Path.of(props.mirrorPath()));
        }
        return MirrorStore.noop();
    }

    @Bean
    @ConditionalOnMissingBean
    public SlashCommandRegistry kairoGatewaySlashCommands() {
        return new SlashCommandRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public GatewayHookRegistry kairoGatewayHooks() {
        return new GatewayHookRegistry();
    }

    /**
     * Wire the gateway and (optionally) start it as part of bean init. Spring calls {@code
     * stopBlocking} on context teardown via {@code destroyMethod} — same lifecycle the cron starter
     * uses. Blocks during start so adapter connect failures fail bootstrap loudly instead of
     * leaving a half-connected gateway behind a healthy-looking bean.
     */
    @Bean(destroyMethod = "stopBlocking")
    @ConditionalOnMissingBean
    public StartableGateway kairoGateway(
            List<Channel> adapters, SessionDirectory sessions, MirrorStore mirror) {
        log.info(
                "Wiring kairo-gateway with {} adapter(s): {}",
                adapters.size(),
                adapters.stream().map(Channel::id).toList());
        StartableGateway wrapper =
                new StartableGateway(new DefaultGateway(adapters, sessions, mirror));
        if (props.isAutoStart()) {
            wrapper.delegate().start().block();
            log.info("Kairo gateway started");
        }
        return wrapper;
    }

    /**
     * Thin wrapper that exposes a Spring-friendly {@code stopBlocking} destroy method while still
     * implementing the {@link Gateway} SPI. Callers depend on the {@link Gateway} interface, so
     * this stays an implementation detail of the auto-config.
     */
    public static final class StartableGateway implements Gateway {
        private final Gateway delegate;

        StartableGateway(Gateway delegate) {
            this.delegate = delegate;
        }

        Gateway delegate() {
            return delegate;
        }

        public void stopBlocking() {
            try {
                delegate.stop().block();
            } catch (Exception e) {
                log.warn("Kairo gateway stop failed: {}", e.toString());
            }
        }

        @Override
        public java.util.Collection<Channel> adapters() {
            return delegate.adapters();
        }

        @Override
        public java.util.Optional<Channel> adapter(String channelId) {
            return delegate.adapter(channelId);
        }

        @Override
        public reactor.core.publisher.Mono<Void> start() {
            return delegate.start();
        }

        @Override
        public reactor.core.publisher.Mono<Void> stop() {
            return delegate.stop();
        }

        @Override
        public reactor.core.publisher.Flux<io.kairo.api.gateway.ChannelMessage> inbound() {
            return delegate.inbound();
        }

        @Override
        public reactor.core.publisher.Mono<java.util.List<io.kairo.api.gateway.SendResult>> deliver(
                io.kairo.api.gateway.DeliveryTarget target,
                String content,
                java.util.Map<String, Object> metadata) {
            return delegate.deliver(target, content, metadata);
        }
    }
}
