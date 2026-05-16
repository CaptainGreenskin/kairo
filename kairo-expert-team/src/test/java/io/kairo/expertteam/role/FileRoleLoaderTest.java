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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileRoleLoaderTest {

    @TempDir Path tempDir;

    @Test
    void loadsValidRoleFromJsonFile() throws IOException {
        String json =
                """
                {
                  "roleId": "custom:devops",
                  "roleName": "DevOps Engineer",
                  "instructions": "You handle infrastructure and deployment.",
                  "skillProfile": "CI/CD, Docker, Kubernetes",
                  "capabilities": {
                    "languages": ["shell", "yaml"],
                    "frameworks": ["docker", "kubernetes"],
                    "domains": ["devops"],
                    "actions": ["implement", "debug"]
                  },
                  "mountedSkills": ["deploy-skill"],
                  "modelOverride": "gpt-4o",
                  "allowedTools": ["run_command"]
                }
                """;
        Files.writeString(tempDir.resolve("devops.json"), json, StandardCharsets.UTF_8);

        List<ExpertProfile> profiles = FileRoleLoader.loadFromDirectory(tempDir);

        assertThat(profiles).hasSize(1);
        ExpertProfile p = profiles.get(0);
        assertThat(p.roleId()).isEqualTo("custom:devops");
        assertThat(p.roleDefinition().roleName()).isEqualTo("DevOps Engineer");
        assertThat(p.roleDefinition().instructions())
                .isEqualTo("You handle infrastructure and deployment.");
        assertThat(p.skillProfile()).isEqualTo("CI/CD, Docker, Kubernetes");
        assertThat(p.capabilities().languages()).containsExactlyInAnyOrder("shell", "yaml");
        assertThat(p.capabilities().frameworks()).containsExactlyInAnyOrder("docker", "kubernetes");
        assertThat(p.capabilities().domains()).containsExactly("devops");
        assertThat(p.capabilities().actions()).containsExactlyInAnyOrder("implement", "debug");
        assertThat(p.mountedSkills()).containsExactly("deploy-skill");
        assertThat(p.modelOverride()).isEqualTo("gpt-4o");
        assertThat(p.roleDefinition().allowedTools()).containsExactly("run_command");
    }

    @Test
    void loadsMinimalRole() throws IOException {
        String json =
                """
                {
                  "roleId": "custom:simple",
                  "instructions": "Simple role."
                }
                """;
        Files.writeString(tempDir.resolve("simple.json"), json, StandardCharsets.UTF_8);

        List<ExpertProfile> profiles = FileRoleLoader.loadFromDirectory(tempDir);

        assertThat(profiles).hasSize(1);
        ExpertProfile p = profiles.get(0);
        assertThat(p.roleId()).isEqualTo("custom:simple");
        assertThat(p.capabilities()).isEqualTo(RoleCapabilities.EMPTY);
        assertThat(p.mountedSkills()).isEmpty();
        assertThat(p.modelOverride()).isNull();
    }

    @Test
    void skipsMalformedFile() throws IOException {
        Files.writeString(tempDir.resolve("bad.json"), "NOT JSON", StandardCharsets.UTF_8);
        Files.writeString(
                tempDir.resolve("good.json"),
                "{\"roleId\":\"custom:ok\",\"instructions\":\"works\"}",
                StandardCharsets.UTF_8);

        List<ExpertProfile> profiles = FileRoleLoader.loadFromDirectory(tempDir);

        assertThat(profiles).hasSize(1);
        assertThat(profiles.get(0).roleId()).isEqualTo("custom:ok");
    }

    @Test
    void skipsMissingRequiredField() throws IOException {
        String json = "{\"roleName\":\"NoId\",\"instructions\":\"missing roleId\"}";
        Files.writeString(tempDir.resolve("noid.json"), json, StandardCharsets.UTF_8);

        List<ExpertProfile> profiles = FileRoleLoader.loadFromDirectory(tempDir);
        assertThat(profiles).isEmpty();
    }

    @Test
    void returnsEmptyForNonExistentDirectory() {
        Path nonExistent = tempDir.resolve("does-not-exist");
        List<ExpertProfile> profiles = FileRoleLoader.loadFromDirectory(nonExistent);
        assertThat(profiles).isEmpty();
    }

    @Test
    void ignoresNonJsonFiles() throws IOException {
        Files.writeString(tempDir.resolve("readme.txt"), "Not a role file", StandardCharsets.UTF_8);
        Files.writeString(
                tempDir.resolve("role.json"),
                "{\"roleId\":\"custom:x\",\"instructions\":\"ok\"}",
                StandardCharsets.UTF_8);

        List<ExpertProfile> profiles = FileRoleLoader.loadFromDirectory(tempDir);
        assertThat(profiles).hasSize(1);
    }

    @Test
    void loadIntoRegistryRegistersRoles() throws IOException {
        String json =
                """
                {
                  "roleId": "custom:loader-test",
                  "instructions": "Loaded via loadIntoRegistry"
                }
                """;
        Files.writeString(tempDir.resolve("test.json"), json, StandardCharsets.UTF_8);

        ExpertRoleRegistry registry = new ExpertRoleRegistry();
        int loaded = FileRoleLoader.loadIntoRegistry(tempDir, registry);

        assertThat(loaded).isEqualTo(1);
        assertThat(registry.resolve("custom:loader-test")).isPresent();
    }

    @Test
    void loadsMultipleFiles() throws IOException {
        Files.writeString(
                tempDir.resolve("a.json"),
                "{\"roleId\":\"custom:a\",\"instructions\":\"A\"}",
                StandardCharsets.UTF_8);
        Files.writeString(
                tempDir.resolve("b.json"),
                "{\"roleId\":\"custom:b\",\"instructions\":\"B\"}",
                StandardCharsets.UTF_8);

        List<ExpertProfile> profiles = FileRoleLoader.loadFromDirectory(tempDir);
        assertThat(profiles).hasSize(2);
    }
}
