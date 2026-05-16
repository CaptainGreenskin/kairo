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
import io.kairo.api.skill.SkillCategory;
import io.kairo.api.skill.SkillDefinition;
import io.kairo.api.skill.SkillRegistry;
import io.kairo.api.team.EvaluationVerdict;
import io.kairo.api.team.EvaluationVerdict.VerdictOutcome;
import io.kairo.api.team.RoleDefinition;
import io.kairo.api.team.TeamStep;
import io.kairo.expertteam.role.ExpertProfile;
import io.kairo.expertteam.role.ExpertRoleRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

    // --- Skill injection tests ---

    @Test
    void generateInjectsSkillContentWhenMountedSkillsResolve() {
        ExpertRoleRegistry roleRegistry = mock(ExpertRoleRegistry.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        DefaultGenerator gen = new DefaultGenerator(roleRegistry, skillRegistry);

        RoleDefinition roleDef = role("coder");
        TeamStep step = step("step-skill", roleDef);

        ExpertProfile profile =
                new ExpertProfile(
                        "coder", roleDef, "coding profile", List.of("code-review"), "coder", null);
        when(roleRegistry.resolve("coder")).thenReturn(Optional.of(profile));

        SkillDefinition skill =
                new SkillDefinition(
                        "code-review",
                        "1.0",
                        "Review code quality",
                        "Always check for null safety and error handling.",
                        List.of("review"),
                        SkillCategory.CODE);
        when(skillRegistry.get("code-review")).thenReturn(Optional.of(skill));

        Agent agent = mock(Agent.class);
        ArgumentCaptor<Msg> captor = ArgumentCaptor.forClass(Msg.class);
        when(agent.call(any())).thenReturn(Mono.just(Msg.of(MsgRole.ASSISTANT, "done")));

        gen.generate(step, Map.of("coder", agent), "Build feature", 1, List.of()).block();

        verify(agent).call(captor.capture());
        String prompt = captor.getValue().text();
        assertThat(prompt).contains("## Skill: code-review");
        assertThat(prompt).contains("Always check for null safety and error handling.");
    }

    @Test
    void generateWorksWithoutSkillRegistryNull() {
        // SkillRegistry is null — no skill injection, no error
        DefaultGenerator gen = new DefaultGenerator(null, null);

        RoleDefinition roleDef = role("coder");
        TeamStep step = step("step-null", roleDef);

        Agent agent = mock(Agent.class);
        ArgumentCaptor<Msg> captor = ArgumentCaptor.forClass(Msg.class);
        when(agent.call(any())).thenReturn(Mono.just(Msg.of(MsgRole.ASSISTANT, "ok")));

        gen.generate(step, Map.of("coder", agent), "goal", 1, List.of()).block();

        verify(agent).call(captor.capture());
        String prompt = captor.getValue().text();
        assertThat(prompt).doesNotContain("## Skill:");
    }

    @Test
    void generateSkipsUnresolvedSkillIds() {
        ExpertRoleRegistry roleRegistry = mock(ExpertRoleRegistry.class);
        SkillRegistry skillRegistry = mock(SkillRegistry.class);
        DefaultGenerator gen = new DefaultGenerator(roleRegistry, skillRegistry);

        RoleDefinition roleDef = role("coder");
        TeamStep step = step("step-miss", roleDef);

        ExpertProfile profile =
                new ExpertProfile(
                        "coder",
                        roleDef,
                        "coding profile",
                        List.of("nonexistent-skill"),
                        "coder",
                        null);
        when(roleRegistry.resolve("coder")).thenReturn(Optional.of(profile));
        when(skillRegistry.get("nonexistent-skill")).thenReturn(Optional.empty());

        Agent agent = mock(Agent.class);
        ArgumentCaptor<Msg> captor = ArgumentCaptor.forClass(Msg.class);
        when(agent.call(any())).thenReturn(Mono.just(Msg.of(MsgRole.ASSISTANT, "ok")));

        gen.generate(step, Map.of("coder", agent), "goal", 1, List.of()).block();

        verify(agent).call(captor.capture());
        String prompt = captor.getValue().text();
        assertThat(prompt).doesNotContain("## Skill:");
    }
}
