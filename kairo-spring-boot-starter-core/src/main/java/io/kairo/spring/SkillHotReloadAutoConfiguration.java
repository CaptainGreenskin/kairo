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
import io.kairo.skill.SkillHotReloadWatcher;
import io.kairo.skill.SkillLoader;
import io.kairo.spring.config.SkillProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for {@link SkillHotReloadWatcher}.
 *
 * <p>Activated only when {@code kairo.skill.hot-reload.enabled=true} is set — off by default to
 * avoid accidentally watching files in production. Implements {@link SmartLifecycle} so the watcher
 * starts and stops with the Spring context.
 */
@AutoConfiguration(after = {AgentRuntimeAutoConfiguration.class})
@ConditionalOnProperty(
        name = "kairo.skill.hot-reload.enabled",
        havingValue = "true",
        matchIfMissing = false)
@ConditionalOnBean(SkillLoader.class)
@EnableConfigurationProperties(SkillProperties.class)
public class SkillHotReloadAutoConfiguration {

    private static final Logger log =
            LoggerFactory.getLogger(SkillHotReloadAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SkillHotReloadLifecycle skillHotReloadLifecycle(
            SkillLoader skillLoader,
            SkillRegistry skillRegistry,
            SkillProperties skillProperties,
            org.springframework.core.env.Environment env) {

        String directoryOverride = env.getProperty("kairo.skill.hot-reload.directory");
        Path watchDir = resolveWatchDirectory(directoryOverride, skillProperties.getSearchPaths());

        if (watchDir == null) {
            log.warn(
                    "SkillHotReload enabled but no watchable directory found. "
                            + "Set kairo.skill.hot-reload.directory or add a local path to kairo.skills.search-paths");
            return new SkillHotReloadLifecycle(null);
        }
        if (!Files.isDirectory(watchDir)) {
            log.warn(
                    "SkillHotReload watch directory does not exist: {} — watcher not started",
                    watchDir);
            return new SkillHotReloadLifecycle(null);
        }

        log.info("SkillHotReload will watch: {}", watchDir);
        SkillHotReloadWatcher watcher =
                new SkillHotReloadWatcher(watchDir, skillLoader, skillRegistry);
        return new SkillHotReloadLifecycle(watcher);
    }

    private Path resolveWatchDirectory(String directoryOverride, List<String> searchPaths) {
        if (directoryOverride != null && !directoryOverride.isBlank()) {
            return resolvePath(directoryOverride);
        }
        for (String sp : searchPaths) {
            if (sp.startsWith("classpath:")) continue;
            Path resolved = resolvePath(sp);
            if (resolved != null) return resolved;
        }
        return null;
    }

    private Path resolvePath(String path) {
        if (path.startsWith("~/")) {
            return Path.of(System.getProperty("user.home")).resolve(path.substring(2));
        }
        return Path.of(path);
    }

    /** Wraps {@link SkillHotReloadWatcher} as a {@link SmartLifecycle} bean. */
    public static final class SkillHotReloadLifecycle implements SmartLifecycle {

        private final SkillHotReloadWatcher watcher;
        private volatile boolean running;

        SkillHotReloadLifecycle(SkillHotReloadWatcher watcher) {
            this.watcher = watcher;
        }

        @Override
        public void start() {
            if (watcher == null) return;
            try {
                watcher.start();
                running = true;
            } catch (IOException e) {
                log.error("Failed to start SkillHotReloadWatcher", e);
            }
        }

        @Override
        public void stop() {
            if (watcher != null) {
                watcher.stop();
            }
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public int getPhase() {
            return Integer.MAX_VALUE - 100;
        }
    }
}
