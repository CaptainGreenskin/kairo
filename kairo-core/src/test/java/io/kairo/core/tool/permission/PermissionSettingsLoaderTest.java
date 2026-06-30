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
package io.kairo.core.tool.permission;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.tool.ToolPermission;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PermissionSettingsLoaderTest {

    @TempDir Path tempDir;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void loadFromSingleFile() throws IOException {
        Path config = tempDir.resolve("permissions.json");
        Files.writeString(
                config,
                """
                {
                  "mode": "strict",
                  "allow": ["Read", "Bash(npm test*)"],
                  "deny": ["Bash(rm -rf*)"]
                }
                """);

        PermissionSettingsLoader loader =
                new PermissionSettingsLoader(objectMapper, List.of(config));
        PermissionSettings settings = loader.load();

        assertThat(settings.mode()).isEqualTo(PermissionMode.STRICT);
        assertThat(settings.rules()).hasSize(3);
        assertThat(settings.rules().get(0).permission()).isEqualTo(ToolPermission.DENIED);
        assertThat(settings.rules().get(0).toolPattern()).isEqualTo("bash");
        assertThat(settings.rules().get(1).permission()).isEqualTo(ToolPermission.ALLOWED);
        assertThat(settings.rules().get(2).permission()).isEqualTo(ToolPermission.ALLOWED);
    }

    @Test
    void loadModeOnly() throws IOException {
        Path config = tempDir.resolve("permissions.json");
        Files.writeString(
                config,
                """
                { "mode": "bypass" }
                """);

        PermissionSettingsLoader loader =
                new PermissionSettingsLoader(objectMapper, List.of(config));
        PermissionSettings settings = loader.load();

        assertThat(settings.mode()).isEqualTo(PermissionMode.BYPASS);
        assertThat(settings.rules()).isEmpty();
    }

    @Test
    void loadModeAliases() throws IOException {
        // Users intuitively type "allow"/"ask"/"readonly"; these must map to the real modes
        // instead of being silently dropped (which would fall back to DEFAULT and surprise them
        // with approval prompts).
        for (String alias : List.of("allow", "auto", "AUTO_APPROVE")) {
            Path config = tempDir.resolve("perm-" + alias + ".json");
            Files.writeString(config, "{\"mode\": \"" + alias + "\"}");
            PermissionSettings settings =
                    new PermissionSettingsLoader(objectMapper, List.of(config)).load();
            assertThat(settings.mode())
                    .as("alias '%s' should map to BYPASS", alias)
                    .isEqualTo(PermissionMode.BYPASS);
        }
        Path askCfg = tempDir.resolve("perm-ask.json");
        Files.writeString(askCfg, "{\"mode\": \"ask\"}");
        assertThat(new PermissionSettingsLoader(objectMapper, List.of(askCfg)).load().mode())
                .isEqualTo(PermissionMode.STRICT);
        Path roCfg = tempDir.resolve("perm-readonly.json");
        Files.writeString(roCfg, "{\"mode\": \"readonly\"}");
        assertThat(new PermissionSettingsLoader(objectMapper, List.of(roCfg)).load().mode())
                .isEqualTo(PermissionMode.PLAN);
    }

    @Test
    void loadRulesOnly() throws IOException {
        Path config = tempDir.resolve("permissions.json");
        Files.writeString(
                config,
                """
                {
                  "allow": ["Read"],
                  "deny": ["Write(/etc/*)"]
                }
                """);

        PermissionSettingsLoader loader =
                new PermissionSettingsLoader(objectMapper, List.of(config));
        PermissionSettings settings = loader.load();

        assertThat(settings.mode()).isNull();
        assertThat(settings.rules()).hasSize(2);
    }

    @Test
    void loadMissingFileReturnsDefaults() {
        Path missing = tempDir.resolve("nonexistent.json");
        PermissionSettingsLoader loader =
                new PermissionSettingsLoader(objectMapper, List.of(missing));
        PermissionSettings settings = loader.load();

        assertThat(settings.mode()).isNull();
        assertThat(settings.rules()).isEmpty();
    }

    @Test
    void loadMalformedJsonSkipsFile() throws IOException {
        Path config = tempDir.resolve("permissions.json");
        Files.writeString(config, "not json {{{");

        PermissionSettingsLoader loader =
                new PermissionSettingsLoader(objectMapper, List.of(config));
        PermissionSettings settings = loader.load();

        assertThat(settings.mode()).isNull();
        assertThat(settings.rules()).isEmpty();
    }

    @Test
    void loadUnknownModeIgnored() throws IOException {
        Path config = tempDir.resolve("permissions.json");
        Files.writeString(
                config,
                """
                {
                  "mode": "nonexistent_mode",
                  "allow": ["Read"]
                }
                """);

        PermissionSettingsLoader loader =
                new PermissionSettingsLoader(objectMapper, List.of(config));
        PermissionSettings settings = loader.load();

        assertThat(settings.mode()).isNull();
        assertThat(settings.rules()).hasSize(1);
    }

    @Test
    void loadMalformedRuleSkipped() throws IOException {
        Path config = tempDir.resolve("permissions.json");
        Files.writeString(
                config,
                """
                {
                  "allow": ["Read", "Bash(missing paren", "Write"]
                }
                """);

        PermissionSettingsLoader loader =
                new PermissionSettingsLoader(objectMapper, List.of(config));
        PermissionSettings settings = loader.load();

        assertThat(settings.rules()).hasSize(2);
        assertThat(settings.rules().get(0).toolPattern()).isEqualTo("read");
        assertThat(settings.rules().get(1).toolPattern()).isEqualTo("write");
    }

    @Test
    void threeLayerMerge() throws IOException {
        Path userConfig = tempDir.resolve("user.json");
        Files.writeString(
                userConfig,
                """
                {
                  "mode": "default",
                  "allow": ["Read"]
                }
                """);

        Path projectConfig = tempDir.resolve("project.json");
        Files.writeString(
                projectConfig,
                """
                {
                  "deny": ["Bash(rm *)"],
                  "allow": ["Bash(npm test*)"]
                }
                """);

        Path localConfig = tempDir.resolve("local.json");
        Files.writeString(
                localConfig,
                """
                {
                  "mode": "strict",
                  "allow": ["Bash(git push*)"]
                }
                """);

        PermissionSettingsLoader loader =
                new PermissionSettingsLoader(
                        objectMapper, List.of(userConfig, projectConfig, localConfig));
        PermissionSettings settings = loader.load();

        assertThat(settings.mode()).isEqualTo(PermissionMode.STRICT);

        List<PermissionRule> rules = settings.rules();
        long denyCount =
                rules.stream().filter(r -> r.permission() == ToolPermission.DENIED).count();
        long allowCount =
                rules.stream().filter(r -> r.permission() == ToolPermission.ALLOWED).count();
        assertThat(denyCount).isEqualTo(1);
        assertThat(allowCount).isEqualTo(3);

        assertThat(rules.get(0).permission()).isEqualTo(ToolPermission.DENIED);
    }

    @Test
    void modeCaseInsensitive() throws IOException {
        Path config = tempDir.resolve("permissions.json");
        Files.writeString(
                config,
                """
                { "mode": "PLAN" }
                """);

        PermissionSettingsLoader loader =
                new PermissionSettingsLoader(objectMapper, List.of(config));
        assertThat(loader.load().mode()).isEqualTo(PermissionMode.PLAN);
    }

    @Test
    void emptyAllowAndDenyLists() throws IOException {
        Path config = tempDir.resolve("permissions.json");
        Files.writeString(
                config,
                """
                {
                  "mode": "default",
                  "allow": [],
                  "deny": []
                }
                """);

        PermissionSettingsLoader loader =
                new PermissionSettingsLoader(objectMapper, List.of(config));
        PermissionSettings settings = loader.load();

        assertThat(settings.mode()).isEqualTo(PermissionMode.DEFAULT);
        assertThat(settings.rules()).isEmpty();
    }

    // -- PermissionSettings.merge --

    @Test
    void mergeHigherModeWins() {
        PermissionSettings base = new PermissionSettings(PermissionMode.DEFAULT, List.of());
        PermissionSettings higher = new PermissionSettings(PermissionMode.STRICT, List.of());

        PermissionSettings merged = base.merge(higher);
        assertThat(merged.mode()).isEqualTo(PermissionMode.STRICT);
    }

    @Test
    void mergeNullModeInherits() {
        PermissionSettings base = new PermissionSettings(PermissionMode.STRICT, List.of());
        PermissionSettings higher = new PermissionSettings(null, List.of());

        PermissionSettings merged = base.merge(higher);
        assertThat(merged.mode()).isEqualTo(PermissionMode.STRICT);
    }

    @Test
    void mergeDenyBeforeAllow() {
        PermissionRule allow = PermissionRule.parse("Read", ToolPermission.ALLOWED);
        PermissionRule deny = PermissionRule.parse("Write", ToolPermission.DENIED);

        PermissionSettings base = new PermissionSettings(null, List.of(allow));
        PermissionSettings higher = new PermissionSettings(null, List.of(deny));

        PermissionSettings merged = base.merge(higher);
        assertThat(merged.rules().get(0).permission()).isEqualTo(ToolPermission.DENIED);
        assertThat(merged.rules().get(1).permission()).isEqualTo(ToolPermission.ALLOWED);
    }
}
