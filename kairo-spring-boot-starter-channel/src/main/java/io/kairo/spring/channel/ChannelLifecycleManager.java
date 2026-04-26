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

import io.kairo.api.channel.Channel;
import io.kairo.api.channel.ChannelInboundHandler;
import io.kairo.channel.ChannelRegistry;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * Registers every discovered {@link Channel} bean in the shared {@link ChannelRegistry} and, when
 * auto-start is enabled, invokes {@link Channel#start(ChannelInboundHandler)} with the configured
 * inbound handler.
 *
 * <p>Stop is called symmetrically on application shutdown so adapters can drain cleanly.
 *
 * @since v0.9
 */
public class ChannelLifecycleManager implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ChannelLifecycleManager.class);
    private static final Duration START_STOP_TIMEOUT = Duration.ofSeconds(10);

    private final ChannelRegistry registry;
    private final List<Channel> channels;
    private final ChannelInboundHandler handler;
    private final boolean autoStart;

    public ChannelLifecycleManager(
            ChannelRegistry registry,
            List<Channel> channels,
            ChannelInboundHandler handler,
            boolean autoStart) {
        this.registry = registry;
        this.channels = channels;
        this.handler = handler;
        this.autoStart = autoStart;
    }

    @Override
    public void afterPropertiesSet() {
        for (Channel channel : channels) {
            registry.register(channel);
            if (autoStart) {
                try {
                    channel.start(handler).block(START_STOP_TIMEOUT);
                    log.info("Started channel '{}'", channel.id());
                } catch (RuntimeException ex) {
                    log.error("Failed to start channel '{}'", channel.id(), ex);
                    throw ex;
                }
            }
        }
    }

    @Override
    public void destroy() {
        for (Channel channel : channels) {
            try {
                channel.stop().block(START_STOP_TIMEOUT);
            } catch (RuntimeException ex) {
                log.warn("Failed to stop channel '{}'", channel.id(), ex);
            } finally {
                registry.unregister(channel.id());
            }
        }
    }
}
