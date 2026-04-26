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

import io.kairo.api.skill.SkillRegistry;
import io.kairo.core.tool.AnnotationToolScanner;
import io.kairo.core.tool.DefaultToolRegistry;
import io.kairo.skill.DefaultSkillRegistry;
import io.kairo.skill.SkillLoader;
import io.kairo.tools.skill.SkillListTool;
import io.kairo.tools.skill.SkillLoadTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Skill infrastructure auto-configuration: skill registry, skill loader, and skill tools.
 *
 * <p>Imported by {@link AgentRuntimeAutoConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
class SkillAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SkillAutoConfiguration.class);

    // ---- Skill Infrastructure ----

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            name = "kairo.skills.enabled",
            havingValue = "true",
            matchIfMissing = true)
    SkillRegistry skillRegistry() {
        return new DefaultSkillRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            name = "kairo.skills.enabled",
            havingValue = "true",
            matchIfMissing = true)
    SkillLoader skillLoader(SkillRegistry skillRegistry) {
        return new SkillLoader(skillRegistry);
    }

    @Bean
    @ConditionalOnProperty(
            name = "kairo.skills.enabled",
            havingValue = "true",
            matchIfMissing = true)
    SkillListTool skillListTool(SkillRegistry skillRegistry, DefaultToolRegistry toolRegistry) {
        SkillListTool tool = new SkillListTool(skillRegistry);
        var scanner = new AnnotationToolScanner();
        toolRegistry.register(scanner.scanClass(SkillListTool.class));
        toolRegistry.registerInstance("skill_list", tool);
        log.info("Registered skill_list tool");
        return tool;
    }

    @Bean
    @ConditionalOnProperty(
            name = "kairo.skills.enabled",
            havingValue = "true",
            matchIfMissing = true)
    SkillLoadTool skillLoadTool(
            SkillRegistry skillRegistry,
            SkillLoader skillLoader,
            DefaultToolRegistry toolRegistry) {
        SkillLoadTool tool = new SkillLoadTool(skillRegistry, skillLoader);
        var scanner = new AnnotationToolScanner();
        toolRegistry.register(scanner.scanClass(SkillLoadTool.class));
        toolRegistry.registerInstance("skill_load", tool);
        log.info("Registered skill_load tool");
        return tool;
    }
}
