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
package io.kairo.expertteam.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.kairo.api.agent.Agent;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.EvaluationVerdict.VerdictOutcome;
import io.kairo.api.team.RoleDefinition;
import io.kairo.api.team.TeamStep;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefaultGeneratorTest {

    private DefaultGenerator generator;

    private static RoleDefinition role(String roleId) {
        return new RoleDefinition(roleId, "Writer", "Write good code.", "coding", List.of());
    }

    private static TeamStep step(String stepId, RoleDefinition role) {
        return new TeamStep(stepId, "Implement the feature", role, List.of(), 0);
    }

    @BeforeEach
    void setUp() {
        generator = new DefaultGenerator();
    }

    @Test
    void generateReturnsAgentResponseText() {
        Agent agent = mock(Agent.class);
        when(agent.call(any())).thenReturn(Mono.just(Msg.of(MsgRole.ASSISTANT, "Generated text")));

        RoleDefinition role = role("writer");
        TeamStep step = step("step-1", role);

        StepVerifier.create(
                        generator.generate(
                                step, Map.of("writer", agent), "Build a REST API", 1, List.of()))
                .expectNext("Generated text")
                .verifyComplete();
    }

    @Test
    void generateCallsAgentWithUserMessage() {
        Agent agent = mock(Agent.class);
        when(agent.call(any())).thenReturn(Mono.just(Msg.of(MsgRole.ASSISTANT, "ok")));

        RoleDefinition role = role("coder");
        TeamStep step = step("step-2", role);

        generator.generate(step, Map.of("coder", agent), "goal", 1, List.of()).block();

        verify(agent).call(any(Msg.class));
    }

    @Test
    void generateReturnsErrorWhenNoAgentBoundToRole() {
        RoleDefinition role = role("missing-role");
        TeamStep step = step("step-3", role);

        StepVerifier.create(generator.generate(step, Map.of(), "goal", 1, List.of()))
                .expectErrorSatisfies(
                        e -> {
                            assertThat(e).isInstanceOf(IllegalStateException.class);
                            assertThat(e.getMessage()).contains("missing-role");
                            assertThat(e.getMessage()).contains("step-3");
                        })
                .verify();
    }

    @Test
    void generatePropagatesAgentError() {
        Agent agent = mock(Agent.class);
        when(agent.call(any())).thenReturn(Mono.error(new RuntimeException("model failed")));

        RoleDefinition role = role("writer");
        TeamStep step = step("step-4", role);

        StepVerifier.create(generator.generate(step, Map.of("writer", agent), "goal", 1, List.of()))
                .expectErrorMessage("model failed")
                .verify();
    }

    @Test
    void generateNullStepThrowsNullPointerException() {
        assertThrows(
                NullPointerException.class,
                () -> generator.generate(null, Map.of(), "goal", 1, List.of()));
    }

    @Test
    void generateNullRoleBindingsThrowsNullPointerException() {
        RoleDefinition role = role("r");
        TeamStep step = step("s", role);

        assertThrows(
                NullPointerException.class,
                () -> generator.generate(step, null, "goal", 1, List.of()));
    }

    @Test
    void generateNullGoalThrowsNullPointerException() {
        RoleDefinition role = role("r");
        TeamStep step = step("s", role);

        assertThrows(
                NullPointerException.class,
                () -> generator.generate(step, Map.of(), null, 1, List.of()));
    }

    @Test
    void generateNullPriorVerdictsThrowsNullPointerException() {
        RoleDefinition role = role("r");
        TeamStep step = step("s", role);

        assertThrows(
                NullPointerException.class,
                () -> generator.generate(step, Map.of(), "goal", 1, null));
    }

    @Test
    void generateWithRevisionAttemptIncludesFeedbackInPrompt() {
        Agent agent = mock(Agent.class);
        when(agent.call(any())).thenReturn(Mono.just(Msg.of(MsgRole.ASSISTANT, "revised")));

        RoleDefinition role = role("editor");
        TeamStep step = step("step-5", role);

        EvaluationVerdict verdict =
                new EvaluationVerdict(
                        VerdictOutcome.REVISE,
                        0.4,
                        "Needs more detail",
                        List.of("Add examples", "Clarify section 2"),
                        Instant.now());

        StepVerifier.create(
                        generator.generate(
                                step, Map.of("editor", agent), "Write docs", 2, List.of(verdict)))
                .expectNext("revised")
                .verifyComplete();
    }

    @Test
    void generateFirstAttemptWithVerdictsDoesNotIncludeRevisionSection() {
        Agent agent = mock(Agent.class);
        when(agent.call(any())).thenReturn(Mono.just(Msg.of(MsgRole.ASSISTANT, "output")));

        RoleDefinition role = role("dev");
        TeamStep step = step("step-6", role);

        EvaluationVerdict verdict =
                new EvaluationVerdict(
                        VerdictOutcome.PASS, 1.0, "looks good", List.of(), Instant.now());

        StepVerifier.create(
                        generator.generate(step, Map.of("dev", agent), "goal", 1, List.of(verdict)))
                .expectNext("output")
                .verifyComplete();
    }
}
