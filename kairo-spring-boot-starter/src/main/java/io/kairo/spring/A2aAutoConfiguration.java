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

import io.kairo.api.a2a.A2aClient;
import io.kairo.api.a2a.AgentCardResolver;
import io.kairo.core.a2a.InProcessA2aClient;
import io.kairo.core.a2a.InProcessAgentCardResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Kairo A2A (Agent-to-Agent) Protocol support.
 *
 * <p>Only activates when {@code kairo-api} A2A classes are on the classpath. Provides default
 * in-process implementations of {@link AgentCardResolver} and {@link A2aClient} that can be
 * overridden by user-defined beans.
 *
 * <p>Disable entirely with {@code kairo.a2a.enabled=false}.
 */
@AutoConfiguration
@ConditionalOnClass(A2aClient.class)
@ConditionalOnProperty(prefix = "kairo.a2a", name = "enabled", matchIfMissing = true)
public class A2aAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(A2aAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public AgentCardResolver agentCardResolver() {
        log.info("Configured in-process AgentCardResolver");
        return new InProcessAgentCardResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public A2aClient a2aClient(AgentCardResolver agentCardResolver) {
        log.info("Configured in-process A2aClient");
        return new InProcessA2aClient(agentCardResolver);
    }
}
