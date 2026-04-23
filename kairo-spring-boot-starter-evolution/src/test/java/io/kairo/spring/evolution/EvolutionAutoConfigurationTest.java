package io.kairo.spring.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.kairo.api.evolution.EvolvedSkillStore;
import io.kairo.api.model.ModelProvider;
import io.kairo.evolution.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EvolutionAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(EvolutionAutoConfiguration.class));

    @Test
    void beansCreatedWhenEnabled() {
        contextRunner
                .withPropertyValues("kairo.evolution.enabled=true")
                .withBean(ModelProvider.class, () -> mock(ModelProvider.class))
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(InMemoryEvolvedSkillStore.class);
                            assertThat(context).hasSingleBean(EvolutionStateMachine.class);
                            assertThat(context)
                                    .hasSingleBean(InMemoryEvolutionRuntimeStateStore.class);
                            assertThat(context).hasSingleBean(DefaultEvolutionTrigger.class);
                            assertThat(context).hasSingleBean(DefaultEvolutionPolicy.class);
                            assertThat(context).hasSingleBean(EvolutionPipelineOrchestrator.class);
                            assertThat(context).hasSingleBean(EvolutionHook.class);
                            assertThat(context).hasSingleBean(SkillContentInjector.class);
                        });
    }

    @Test
    void noBeanWhenDisabled() {
        contextRunner.run(
                context -> {
                    assertThat(context).doesNotHaveBean(InMemoryEvolvedSkillStore.class);
                    assertThat(context).doesNotHaveBean(EvolutionHook.class);
                    assertThat(context).doesNotHaveBean(EvolutionPipelineOrchestrator.class);
                    assertThat(context).doesNotHaveBean(SkillContentInjector.class);
                });
    }

    @Test
    void customBeanOverridesDefault() {
        EvolvedSkillStore customStore = mock(EvolvedSkillStore.class);

        contextRunner
                .withPropertyValues("kairo.evolution.enabled=true")
                .withBean(ModelProvider.class, () -> mock(ModelProvider.class))
                .withBean(EvolvedSkillStore.class, () -> customStore)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(EvolvedSkillStore.class);
                            assertThat(context).doesNotHaveBean(InMemoryEvolvedSkillStore.class);
                            assertThat(context.getBean(EvolvedSkillStore.class))
                                    .isSameAs(customStore);
                        });
    }
}
