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
package io.kairo.expertteam.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.team.RoleDefinition;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExpertRoleRegistryTest {

    private ExpertRoleRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ExpertRoleRegistry();
    }

    @Test
    void shouldRegisterAllBuiltInRoles() {
        Set<String> roleIds = registry.registeredRoleIds();
        assertThat(roleIds).hasSize(6);
        assertThat(roleIds)
                .containsExactlyInAnyOrder(
                        "expert:architect",
                        "expert:researcher",
                        "expert:coder",
                        "expert:reviewer",
                        "expert:tester",
                        "expert:synthesizer");
    }

    @Test
    void shouldResolveBuiltInRole() {
        Optional<ExpertProfile> profile = registry.resolve("expert:coder");
        assertThat(profile).isPresent();
        assertThat(profile.get().roleId()).isEqualTo("expert:coder");
        assertThat(profile.get().roleDefinition().roleId()).isEqualTo("expert:coder");
        assertThat(profile.get().roleDefinition().roleName()).isEqualTo("Coder");
        assertThat(profile.get().skillProfile()).isEqualTo("Code implementation and modification");
        assertThat(profile.get().memoryNamespace()).isEqualTo("expert:coder");
        assertThat(profile.get().modelOverride()).isNull();
    }

    @Test
    void shouldReturnEmptyForUnknownRole() {
        Optional<ExpertProfile> profile = registry.resolve("expert:unknown");
        assertThat(profile).isEmpty();
    }

    @Test
    void shouldRegisterCustomRole() {
        RoleDefinition customDef =
                new RoleDefinition(
                        "custom:devops",
                        "DevOps",
                        "You handle infrastructure and deployment.",
                        "agent.default",
                        List.of("run_command", "read_file"));
        ExpertProfile customProfile =
                new ExpertProfile(
                        "custom:devops",
                        customDef,
                        "Infrastructure and deployment",
                        List.of("deploy", "monitor"),
                        "custom:devops",
                        "gpt-4o");

        registry.register("custom:devops", customProfile);

        Optional<ExpertProfile> resolved = registry.resolve("custom:devops");
        assertThat(resolved).isPresent();
        assertThat(resolved.get().roleId()).isEqualTo("custom:devops");
        assertThat(resolved.get().mountedSkills()).containsExactly("deploy", "monitor");
        assertThat(resolved.get().modelOverride()).isEqualTo("gpt-4o");
    }

    @Test
    void shouldOverrideBuiltInRoleViaRegister() {
        RoleDefinition overrideDef =
                new RoleDefinition(
                        "expert:coder",
                        "SeniorCoder",
                        "You are a senior coder with extensive experience.",
                        "agent.senior",
                        List.of());
        ExpertProfile overrideProfile =
                new ExpertProfile(
                        "expert:coder",
                        overrideDef,
                        "Senior code implementation",
                        List.of("refactor"),
                        "expert:coder:senior",
                        "claude-opus");

        registry.register("expert:coder", overrideProfile);

        Optional<ExpertProfile> resolved = registry.resolve("expert:coder");
        assertThat(resolved).isPresent();
        assertThat(resolved.get().roleDefinition().roleName()).isEqualTo("SeniorCoder");
        assertThat(resolved.get().modelOverride()).isEqualTo("claude-opus");
    }

    @Test
    void shouldListAllProfiles() {
        List<ExpertProfile> all = registry.allProfiles();
        assertThat(all).hasSize(6);
    }

    @Test
    void shouldRejectNullRoleId() {
        RoleDefinition def =
                new RoleDefinition("test:role", "Test", "instructions", "agent.default", List.of());
        ExpertProfile profile =
                new ExpertProfile("test:role", def, "test", List.of(), "test:role", null);
        assertThatThrownBy(() -> registry.register(null, profile))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullProfile() {
        assertThatThrownBy(() -> registry.register("test:role", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void expertRoleFromRoleIdShouldResolveBuiltIn() {
        assertThat(ExpertRole.fromRoleId("expert:architect")).isEqualTo(ExpertRole.ARCHITECT);
        assertThat(ExpertRole.fromRoleId("expert:researcher")).isEqualTo(ExpertRole.RESEARCHER);
        assertThat(ExpertRole.fromRoleId("expert:coder")).isEqualTo(ExpertRole.CODER);
        assertThat(ExpertRole.fromRoleId("expert:reviewer")).isEqualTo(ExpertRole.REVIEWER);
        assertThat(ExpertRole.fromRoleId("expert:tester")).isEqualTo(ExpertRole.TESTER);
        assertThat(ExpertRole.fromRoleId("expert:synthesizer")).isEqualTo(ExpertRole.SYNTHESIZER);
    }

    @Test
    void expertRoleFromRoleIdShouldReturnNullForUnknown() {
        assertThat(ExpertRole.fromRoleId("custom:something")).isNull();
        assertThat(ExpertRole.fromRoleId("")).isNull();
        assertThat(ExpertRole.fromRoleId(null)).isNull();
    }

    @Test
    void reviewerShouldHaveReadOnlyTools() {
        Optional<ExpertProfile> reviewer = registry.resolve("expert:reviewer");
        assertThat(reviewer).isPresent();
        assertThat(reviewer.get().roleDefinition().allowedTools())
                .containsExactly("read_file", "grep", "search", "list_files");
    }

    @Test
    void coderShouldHaveEmptyToolsAllowingAll() {
        Optional<ExpertProfile> coder = registry.resolve("expert:coder");
        assertThat(coder).isPresent();
        assertThat(coder.get().roleDefinition().allowedTools()).isEmpty();
    }
}
