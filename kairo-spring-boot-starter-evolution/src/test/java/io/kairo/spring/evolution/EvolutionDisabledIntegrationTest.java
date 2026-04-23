package io.kairo.spring.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.evolution.EvolvedSkillStore;
import io.kairo.evolution.EvolutionHook;
import io.kairo.evolution.EvolutionPipelineOrchestrator;
import io.kairo.evolution.SkillContentInjector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EvolutionDisabledIntegrationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(EvolutionAutoConfiguration.class));

    @Test
    void zeroBeans() {
        contextRunner.run(
                context -> {
                    assertThat(context).doesNotHaveBean(EvolvedSkillStore.class);
                    assertThat(context).doesNotHaveBean(EvolutionHook.class);
                    assertThat(context).doesNotHaveBean(EvolutionPipelineOrchestrator.class);
                    assertThat(context).doesNotHaveBean(SkillContentInjector.class);
                });
    }

    @Test
    void zeroSideEffects() {
        contextRunner
                .withPropertyValues("kairo.evolution.enabled=false")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(EvolvedSkillStore.class);
                            assertThat(context).doesNotHaveBean(EvolutionHook.class);
                            assertThat(context)
                                    .doesNotHaveBean(EvolutionPipelineOrchestrator.class);
                            assertThat(context).doesNotHaveBean(SkillContentInjector.class);
                            // No evolution beans means no side effects
                            assertThat(context.getBeanDefinitionCount()).isGreaterThan(0);
                        });
    }
}
