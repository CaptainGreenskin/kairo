package io.kairo.spring.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kairo.api.agent.Agent;
import io.kairo.api.agent.AgentBuilderCustomizer;
import io.kairo.api.agent.AgentConfig;
import io.kairo.api.agent.AgentState;
import io.kairo.api.agent.SystemPromptContributor;
import io.kairo.api.evolution.EvolvedSkillStore;
import io.kairo.api.evolution.SkillTrustLevel;
import io.kairo.api.hook.SessionEndEvent;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.evolution.EvolutionHook;
import io.kairo.spring.AgentRuntimeAutoConfiguration;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Mono;

class EvolutionEnabledIntegrationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    AgentRuntimeAutoConfiguration.class,
                                    EvolutionAutoConfiguration.class));

    private ModelProvider mockModelProvider() {
        ModelProvider mp = mock(ModelProvider.class);
        when(mp.call(anyList(), any(ModelConfig.class)))
                .thenAnswer(
                        invocation -> {
                            @SuppressWarnings("unchecked")
                            List<Msg> messages = invocation.getArgument(0);
                            String prompt = messages.isEmpty() ? "" : messages.get(0).text();
                            String text;
                            if (prompt.contains("Review the agent conversation")) {
                                text =
                                        "SKILL_NAME: Incident triage checklist\n"
                                                + "DESCRIPTION: Structured outage triage flow\n"
                                                + "CATEGORY: sre\n"
                                                + "INSTRUCTIONS: Verify blast radius, collect key metrics, then escalate with evidence.";
                            } else if (prompt.contains("Review the conversation")) {
                                text = "NO_MEMORY";
                            } else {
                                text = "Acknowledged.";
                            }

                            ModelResponse response =
                                    new ModelResponse(
                                            "r1",
                                            List.of(new Content.TextContent(text)),
                                            new ModelResponse.Usage(10, 20, 0, 0),
                                            ModelResponse.StopReason.END_TURN,
                                            "test-model");
                            return Mono.just(response);
                        });
        return mp;
    }

    @Test
    void hookTriggersAndWritesToStore() {
        contextRunner
                .withPropertyValues("kairo.evolution.enabled=true")
                .withBean(ModelProvider.class, this::mockModelProvider)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(EvolutionHook.class);
                            assertThat(context).hasSingleBean(EvolvedSkillStore.class);

                            // Verify the store bean is functional
                            EvolvedSkillStore store = context.getBean(EvolvedSkillStore.class);
                            assertThat(store.list().collectList().block()).isEmpty();
                        });
    }

    @Test
    void customizerWiresIntoBuilder() {
        contextRunner
                .withPropertyValues("kairo.evolution.enabled=true")
                .withBean(ModelProvider.class, this::mockModelProvider)
                .run(
                        context -> {
                            assertThat(context).hasBean("evolutionCustomizer");
                            AgentBuilderCustomizer customizer =
                                    context.getBean(
                                            "evolutionCustomizer", AgentBuilderCustomizer.class);
                            assertThat(customizer).isNotNull();
                        });
    }

    @Test
    void defaultAgentConsumesEvolutionWiringAndProducesPromptInjectionSignal() {
        contextRunner
                .withPropertyValues(
                        "kairo.evolution.enabled=true",
                        "kairo.evolution.review-model-name=test-model",
                        "kairo.agent.name=evolution-test-agent",
                        "kairo.agent.max-iterations=12",
                        "kairo.model.model-name=test-model")
                .withBean(ModelProvider.class, this::mockModelProvider)
                .run(
                        context -> {
                            Agent agent = context.getBean(Agent.class);
                            EvolvedSkillStore store = context.getBean(EvolvedSkillStore.class);
                            AgentConfig config = extractConfig(agent);

                            EvolutionHook evolutionHook = extractEvolutionHook(config);
                            Supplier<List<Msg>> historySupplier =
                                    () ->
                                            List.of(
                                                    Msg.of(
                                                            MsgRole.USER,
                                                            "Need a safe outage triage flow."),
                                                    Msg.of(
                                                            MsgRole.ASSISTANT,
                                                            "I used a checklist-driven triage method."));

                            evolutionHook.onSessionEnd(
                                    new SessionEndEvent(
                                            "evolution-test-agent",
                                            AgentState.COMPLETED,
                                            8,
                                            1200L,
                                            Duration.ofSeconds(3),
                                            null,
                                            historySupplier));

                            List<io.kairo.api.evolution.EvolvedSkill> activatedSkills =
                                    awaitValidatedSkills(store);
                            assertThat(activatedSkills).isNotEmpty();
                            assertThat(activatedSkills.get(0).name())
                                    .isEqualTo("Incident triage checklist");

                            String contributedPrompt = collectContributedPrompt(config);
                            assertThat(contributedPrompt).contains("Incident triage checklist");
                            assertThat(contributedPrompt).contains("Verify blast radius");
                        });
    }

    private AgentConfig extractConfig(Agent agent) {
        try {
            Field configField = agent.getClass().getDeclaredField("config");
            configField.setAccessible(true);
            return (AgentConfig) configField.get(agent);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to extract AgentConfig from default agent", e);
        }
    }

    private EvolutionHook extractEvolutionHook(AgentConfig config) {
        List<Object> hooks = config.hooks();
        assertThat(hooks).isNotNull();
        return hooks.stream()
                .filter(EvolutionHook.class::isInstance)
                .map(EvolutionHook.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("EvolutionHook not found in agent hooks"));
    }

    private List<io.kairo.api.evolution.EvolvedSkill> awaitValidatedSkills(
            EvolvedSkillStore store) {
        List<io.kairo.api.evolution.EvolvedSkill> result = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            result = store.listByMinTrust(SkillTrustLevel.VALIDATED).collectList().block();
            if (result != null && !result.isEmpty()) {
                return result;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return result;
    }

    private String collectContributedPrompt(AgentConfig config) {
        List<SystemPromptContributor> contributors = config.systemPromptContributors();
        assertThat(contributors).isNotNull();
        StringBuilder builder = new StringBuilder();
        for (SystemPromptContributor contributor : contributors) {
            String content = contributor.content().block();
            if (content != null && !content.isBlank()) {
                builder.append(content).append('\n');
            }
        }
        return builder.toString();
    }
}
