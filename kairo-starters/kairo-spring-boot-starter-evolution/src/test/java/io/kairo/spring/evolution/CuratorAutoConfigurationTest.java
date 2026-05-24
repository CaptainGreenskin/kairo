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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.evolution.EvolvedSkillStore;
import io.kairo.api.evolution.SkillTelemetryStore;
import io.kairo.api.model.ModelProvider;
import io.kairo.evolution.curator.CuratorActionExecutor;
import io.kairo.evolution.curator.FileSkillTelemetryStore;
import io.kairo.evolution.curator.LifecycleCuratorDaemon;
import io.kairo.evolution.curator.LlmSkillCurator;
import io.kairo.evolution.curator.UmbrellaConsolidationPlanner;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class CuratorAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    EvolutionAutoConfiguration.class,
                                    CuratorAutoConfiguration.class))
                    .withUserConfiguration(TestModelProviderConfig.class);

    @Test
    void disabledByDefault() {
        runner.run(
                ctx -> {
                    assertThat(ctx).doesNotHaveBean(LifecycleCuratorDaemon.class);
                    assertThat(ctx).doesNotHaveBean(UmbrellaConsolidationPlanner.class);
                });
    }

    @Test
    void enabledWiresAllBeans(@TempDir Path dir) {
        runner.withPropertyValues(
                        "kairo.evolution.enabled=true",
                        "kairo.evolution.curator.enabled=true",
                        "kairo.evolution.curator.telemetryDirectory=" + dir.toString())
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(SkillTelemetryStore.class);
                            assertThat(ctx).hasSingleBean(LifecycleCuratorDaemon.class);
                            assertThat(ctx).hasSingleBean(CuratorActionExecutor.class);
                            assertThat(ctx).hasSingleBean(UmbrellaConsolidationPlanner.class);
                            assertThat(ctx).hasSingleBean(LlmSkillCurator.class);

                            FileSkillTelemetryStore store =
                                    (FileSkillTelemetryStore)
                                            ctx.getBean(SkillTelemetryStore.class);
                            assertThat(store.file().getParent()).isEqualTo(dir);
                        });
    }

    @Test
    void customLlmSkillCuratorBeanOverridesDefault(@TempDir Path dir) {
        runner.withPropertyValues(
                        "kairo.evolution.enabled=true",
                        "kairo.evolution.curator.enabled=true",
                        "kairo.evolution.curator.telemetryDirectory=" + dir.toString())
                .withUserConfiguration(CustomCuratorConfig.class)
                .run(
                        ctx -> {
                            LlmSkillCurator curator = ctx.getBean(LlmSkillCurator.class);
                            // Marker check — instance identity confirms our @Bean was picked up.
                            assertThat(curator).isSameAs(ctx.getBean("customCurator"));
                        });
    }

    @Test
    void autoStartFlagBootsTheDaemon(@TempDir Path dir) {
        runner.withPropertyValues(
                        "kairo.evolution.enabled=true",
                        "kairo.evolution.curator.enabled=true",
                        "kairo.evolution.curator.autoStart=true",
                        "kairo.evolution.curator.telemetryDirectory=" + dir.toString())
                .run(
                        ctx -> {
                            LifecycleCuratorDaemon daemon =
                                    ctx.getBean(LifecycleCuratorDaemon.class);
                            assertThat(daemon.isRunning()).isTrue();
                        });
    }

    @Test
    void existingTelemetryStoreBeanIsRespected(@TempDir Path dir) {
        runner.withPropertyValues(
                        "kairo.evolution.enabled=true",
                        "kairo.evolution.curator.enabled=true",
                        "kairo.evolution.curator.telemetryDirectory=" + dir.toString())
                .withUserConfiguration(CustomTelemetryStoreConfig.class)
                .run(
                        ctx -> {
                            SkillTelemetryStore store = ctx.getBean(SkillTelemetryStore.class);
                            // Custom in-memory store wins over the default file-backed one.
                            assertThat(store)
                                    .isInstanceOf(
                                            io.kairo.evolution.curator.InMemorySkillTelemetryStore
                                                    .class);
                        });
    }

    @Configuration
    static class TestModelProviderConfig {

        @Bean
        EvolvedSkillStore evolvedSkillStoreOverride() {
            return new io.kairo.skill.InMemoryEvolvedSkillStore();
        }

        @Bean
        ModelProvider modelProvider() {
            return Mockito.mock(ModelProvider.class);
        }
    }

    @Configuration
    static class CustomCuratorConfig {

        @Bean
        LlmSkillCurator customCurator() {
            return LlmSkillCurator.noop();
        }
    }

    @Configuration
    static class CustomTelemetryStoreConfig {

        @Bean
        SkillTelemetryStore telemetry() {
            return new io.kairo.evolution.curator.InMemorySkillTelemetryStore();
        }
    }
}
