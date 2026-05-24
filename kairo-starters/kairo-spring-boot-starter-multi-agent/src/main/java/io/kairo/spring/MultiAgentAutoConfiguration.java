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

import io.kairo.api.team.MessageBus;
import io.kairo.api.team.TeamManager;
import io.kairo.multiagent.team.DefaultTaskDispatchCoordinator;
import io.kairo.multiagent.team.DefaultTeamManager;
import io.kairo.multiagent.team.InProcessMessageBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Kairo multi-agent coordination support.
 *
 * <p>Only activates when {@code kairo-multi-agent} is on the classpath. Creates an {@link
 * InProcessMessageBus}, {@link DefaultTeamManager}, and {@link DefaultTaskDispatchCoordinator}
 * unless the user provides their own beans or sets {@code kairo.multi-agent.enabled=false}.
 */
@AutoConfiguration
@ConditionalOnClass(DefaultTeamManager.class)
@ConditionalOnProperty(
        prefix = "kairo.multi-agent",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(MultiAgentProperties.class)
public class MultiAgentAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(MessageBus.class)
    public InProcessMessageBus inProcessMessageBus() {
        log.debug("Creating InProcessMessageBus bean");
        return new InProcessMessageBus();
    }

    @Bean
    @ConditionalOnMissingBean(TeamManager.class)
    public DefaultTeamManager defaultTeamManager() {
        return new DefaultTeamManager();
    }

    @Bean
    @ConditionalOnMissingBean(DefaultTaskDispatchCoordinator.class)
    public DefaultTaskDispatchCoordinator defaultTaskDispatchCoordinator() {
        return new DefaultTaskDispatchCoordinator();
    }
}
