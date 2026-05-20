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
package io.kairo.spring.evolution;

import io.kairo.api.evolution.EvolvedSkillStore;
import io.kairo.api.evolution.SkillTelemetryStore;
import io.kairo.evolution.curator.CuratorActionExecutor;
import io.kairo.evolution.curator.CuratorConfig;
import io.kairo.evolution.curator.CuratorIdleSignal;
import io.kairo.evolution.curator.FileSkillTelemetryStore;
import io.kairo.evolution.curator.LifecycleCuratorDaemon;
import io.kairo.evolution.curator.LlmSkillCurator;
import io.kairo.evolution.curator.UmbrellaConsolidationPlanner;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for the curator subsystem (M1 lifecycle + M2 LLM consolidation). Activated
 * only when {@code kairo.evolution.curator.enabled=true}.
 *
 * <p>By default wires:
 *
 * <ul>
 *   <li>{@link FileSkillTelemetryStore} under {@code ~/.kairo/evolution/}.
 *   <li>{@link LifecycleCuratorDaemon} with the configured intervals.
 *   <li>{@link LlmSkillCurator#noop()} as a safe default (no-op). Override the bean to plug in a
 *       real LLM-driven curator.
 *   <li>{@link CuratorActionExecutor} writing demote files into the configured support directory.
 *   <li>{@link UmbrellaConsolidationPlanner} ready for manual {@code runOnce()/dryRun()}.
 * </ul>
 *
 * <p>If {@code kairo.evolution.curator.autoStart=true} the daemon's {@link
 * LifecycleCuratorDaemon#start()} is invoked at bean post-construction and {@link
 * LifecycleCuratorDaemon#stop()} at shutdown.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "kairo.evolution.curator.enabled", havingValue = "true")
public class CuratorAutoConfiguration {

    @Autowired private EvolutionProperties properties;

    @Bean
    @ConditionalOnMissingBean(SkillTelemetryStore.class)
    SkillTelemetryStore skillTelemetryStore() {
        Path dir = resolveTelemetryDir();
        return new FileSkillTelemetryStore(dir);
    }

    @Bean
    @ConditionalOnMissingBean(CuratorIdleSignal.class)
    CuratorIdleSignal curatorIdleSignal() {
        // Server-side hosts have no live agent session — always idle.
        return CuratorIdleSignal.alwaysIdle();
    }

    @Bean
    @ConditionalOnMissingBean(LlmSkillCurator.class)
    LlmSkillCurator llmSkillCurator() {
        // Safe default — override this bean to plug in a real LLM-driven curator.
        return LlmSkillCurator.noop();
    }

    @Bean
    @ConditionalOnMissingBean
    LifecycleCuratorDaemon lifecycleCuratorDaemon(
            SkillTelemetryStore store, CuratorIdleSignal idleSignal) {
        EvolutionProperties.Curator c = properties.getCurator();
        CuratorConfig cfg =
                new CuratorConfig(
                        Duration.ofMinutes(c.getReviewIntervalMinutes()),
                        Duration.ofMinutes(c.getIdleThresholdMinutes()),
                        Duration.ofDays(c.getStaleAfterDays()),
                        Duration.ofDays(c.getArchiveAfterDays()),
                        true,
                        false);
        return new LifecycleCuratorDaemon(store, idleSignal, cfg);
    }

    @Bean
    @ConditionalOnMissingBean
    CuratorActionExecutor curatorActionExecutor(
            EvolvedSkillStore skillStore, SkillTelemetryStore telemetryStore) {
        Path supportDir = resolveSupportDir();
        return new CuratorActionExecutor(skillStore, telemetryStore, supportDir);
    }

    @Bean
    @ConditionalOnMissingBean
    UmbrellaConsolidationPlanner umbrellaConsolidationPlanner(
            EvolvedSkillStore skillStore,
            SkillTelemetryStore telemetryStore,
            LifecycleCuratorDaemon daemon,
            LlmSkillCurator curator,
            CuratorActionExecutor executor,
            CuratorIdleSignal idleSignal) {
        EvolutionProperties.Curator c = properties.getCurator();
        return new UmbrellaConsolidationPlanner(
                skillStore,
                telemetryStore,
                daemon,
                curator,
                executor,
                idleSignal,
                Duration.ofMinutes(c.getIdleThresholdMinutes()));
    }

    @Bean
    @ConditionalOnMissingBean
    CuratorDaemonLifecycle curatorDaemonLifecycle(LifecycleCuratorDaemon daemon) {
        return new CuratorDaemonLifecycle(daemon, properties.getCurator().isAutoStart());
    }

    private Path resolveTelemetryDir() {
        String custom = properties.getCurator().getTelemetryDirectory();
        if (custom != null && !custom.isBlank()) {
            return Paths.get(custom);
        }
        return Paths.get(System.getProperty("user.home"), ".kairo", "evolution");
    }

    private Path resolveSupportDir() {
        String custom = properties.getCurator().getSupportDirectory();
        if (custom != null && !custom.isBlank()) {
            return Paths.get(custom);
        }
        return resolveTelemetryDir().resolve("skills");
    }

    /**
     * Adapter that drives daemon start/stop from Spring's lifecycle events. Kept as a separate bean
     * so the auto-configuration class itself does not self-inject (which Spring rejects).
     */
    static final class CuratorDaemonLifecycle implements SmartLifecycle {
        private final LifecycleCuratorDaemon daemon;
        private final boolean autoStart;
        private volatile boolean running;

        CuratorDaemonLifecycle(LifecycleCuratorDaemon daemon, boolean autoStart) {
            this.daemon = daemon;
            this.autoStart = autoStart;
        }

        @Override
        public boolean isAutoStartup() {
            return autoStart;
        }

        @Override
        public void start() {
            daemon.start();
            running = true;
        }

        @Override
        public void stop() {
            daemon.stop();
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }
    }
}
