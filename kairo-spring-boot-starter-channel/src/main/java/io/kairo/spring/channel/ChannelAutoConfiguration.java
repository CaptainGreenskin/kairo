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
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the Kairo channel starter. Opt-in via {@code kairo.channel.enabled=true}.
 *
 * <p>When enabled and a {@link ChannelInboundHandler} bean is present, all discovered {@link
 * Channel} beans are registered in a shared {@link ChannelRegistry} and started through the {@link
 * ChannelLifecycleManager}. Applications that don't provide a handler opt out of autowiring —
 * matching the deny-safe posture we use elsewhere in v0.9 starters.
 *
 * @since v0.9
 */
@AutoConfiguration
@ConditionalOnClass({Channel.class, ChannelRegistry.class})
@ConditionalOnProperty(
        prefix = "kairo.channel",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
@EnableConfigurationProperties(ChannelProperties.class)
public class ChannelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ChannelRegistry kairoChannelRegistry() {
        return new ChannelRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ChannelInboundHandler.class)
    public ChannelLifecycleManager kairoChannelLifecycleManager(
            ChannelRegistry registry,
            List<Channel> channels,
            ChannelInboundHandler handler,
            ChannelProperties properties) {
        return new ChannelLifecycleManager(registry, channels, handler, properties.isAutoStart());
    }
}
