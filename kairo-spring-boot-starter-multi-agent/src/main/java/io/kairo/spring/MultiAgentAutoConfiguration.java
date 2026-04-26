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

import io.kairo.multiagent.team.DefaultTeamManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

/**
 * Auto-configuration for Kairo multi-agent coordination support.
 *
 * <p>Only activates when {@code kairo-multi-agent} is on the classpath. Future versions will
 * auto-wire {@link io.kairo.api.team.TeamManager} and scheduling beans; for now this serves as the
 * extension point and classpath marker for the starter-multi-agent module.
 */
@AutoConfiguration
@ConditionalOnClass(DefaultTeamManager.class)
public class MultiAgentAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentAutoConfiguration.class);
}
