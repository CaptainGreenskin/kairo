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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Kairo channel starter.
 *
 * <p>The starter is <b>opt-in</b>: setting {@code kairo.channel.enabled=true} is required for the
 * auto-configuration to wire any {@link io.kairo.channel.ChannelRegistry} or lifecycle beans. This
 * keeps the starter inert on a fresh classpath so applications that don't integrate with channels
 * pay no startup cost and expose no unintended SPI surface.
 *
 * @since v0.9
 */
@ConfigurationProperties(prefix = "kairo.channel")
public class ChannelProperties {

    /**
     * Master switch for the channel starter. Default: {@code false}. When false, no channel beans
     * are wired — applications can still construct registries manually.
     */
    private boolean enabled = false;

    /**
     * If true, every registered {@link io.kairo.api.channel.Channel} bean is started during
     * application startup using the sole {@link io.kairo.api.channel.ChannelInboundHandler} bean
     * found in the context. Default: {@code true}.
     *
     * <p>Set to false if you want to own the lifecycle explicitly (e.g., to delay start until after
     * a warm-up hook, or to pick handlers per-channel).
     */
    private boolean autoStart = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }
}
