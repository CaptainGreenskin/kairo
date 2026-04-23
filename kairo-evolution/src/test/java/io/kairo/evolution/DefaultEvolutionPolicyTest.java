package io.kairo.evolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import io.kairo.api.evolution.EvolutionContext;
import io.kairo.api.evolution.EvolutionCounters;
import io.kairo.api.evolution.EvolvedSkillStore;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefaultEvolutionPolicyTest {

    private ModelProvider modelProvider;
    private EvolvedSkillStore skillStore;
    private DefaultEvolutionPolicy policy;

    @BeforeEach
    void setUp() {
        modelProvider = mock(ModelProvider.class);
        skillStore = new InMemoryEvolvedSkillStore();
        policy =
                new DefaultEvolutionPolicy(
                        modelProvider, "test-model", 5, skillStore, Duration.ofSeconds(10));
    }

    private EvolutionContext context(int iterations) {
        List<Msg> history =
                List.of(Msg.of(MsgRole.USER, "Hello"), Msg.of(MsgRole.ASSISTANT, "Hi there"));
        return new EvolutionContext(
                "test-agent", history, iterations, EvolutionCounters.ZERO, 5, 8, 1000L, List.of());
    }

    private ModelResponse textResponse(String text) {
        return new ModelResponse(
                "r1",
                List.of(new Content.TextContent(text)),
                new ModelResponse.Usage(10, 20, 0, 0),
                ModelResponse.StopReason.END_TURN,
                "test-model");
    }

    @Test
    void belowThresholdReturnsEmpty() {
        StepVerifier.create(policy.review(context(2)))
                .assertNext(outcome -> assertThat(outcome.hasChanges()).isFalse())
                .verifyComplete();

        verifyNoInteractions(modelProvider);
    }

    @Test
    void reviewCallsLlm() {
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(textResponse("NO_SKILL")));

        StepVerifier.create(policy.review(context(10)))
                .assertNext(outcome -> assertThat(outcome).isNotNull())
                .verifyComplete();

        verify(modelProvider, atLeastOnce()).call(anyList(), any(ModelConfig.class));
    }

    @Test
    void reviewParsesSkillResponse() {
        String skillResponse =
                "SKILL_NAME: data-cleanup\n"
                        + "DESCRIPTION: Clean messy CSV data\n"
                        + "CATEGORY: data\n"
                        + "INSTRUCTIONS: Use pandas to clean and normalize CSV columns";

        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(textResponse(skillResponse)))
                .thenReturn(Mono.just(textResponse("NO_MEMORY")));

        StepVerifier.create(policy.review(context(10)))
                .assertNext(
                        outcome -> {
                            assertThat(outcome.skillToCreate()).isPresent();
                            assertThat(outcome.skillToCreate().get().name())
                                    .isEqualTo("data-cleanup");
                            assertThat(outcome.skillToCreate().get().instructions())
                                    .contains("pandas");
                        })
                .verifyComplete();
    }

    @Test
    void reviewParsesNoSkillResponse() {
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.just(textResponse("NO_SKILL")));

        StepVerifier.create(policy.review(context(10)))
                .assertNext(outcome -> assertThat(outcome.skillToCreate()).isEmpty())
                .verifyComplete();
    }

    @Test
    void timeoutReturnsEmpty() {
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(
                        Mono.delay(Duration.ofSeconds(30))
                                .then(Mono.just(textResponse("NO_SKILL"))));

        // Policy timeout is 10s
        StepVerifier.create(policy.review(context(10)))
                .assertNext(outcome -> assertThat(outcome.hasChanges()).isFalse())
                .verifyComplete();
    }

    @Test
    void errorReturnsEmpty() {
        when(modelProvider.call(anyList(), any(ModelConfig.class)))
                .thenReturn(Mono.error(new RuntimeException("LLM service unavailable")));

        StepVerifier.create(policy.review(context(10)))
                .assertNext(outcome -> assertThat(outcome.hasChanges()).isFalse())
                .verifyComplete();
    }
}
