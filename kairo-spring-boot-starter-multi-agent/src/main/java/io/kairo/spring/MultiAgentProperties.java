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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Kairo multi-agent support, bound to the {@code kairo.multi-agent}
 * prefix.
 *
 * <p>Example {@code application.yml}:
 *
 * <pre>{@code
 * kairo:
 *   multi-agent:
 *     enabled: true
 * }</pre>
 */
@ConfigurationProperties(prefix = "kairo.multi-agent")
public class MultiAgentProperties {

    /** Whether multi-agent coordination is enabled. Defaults to {@code true}. */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
