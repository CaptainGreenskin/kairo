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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.tool.ToolPermission;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PermissionRuleEngineTest {

    // -- PermissionRule.parse --

    @Test
    void parseSimpleToolName() {
        PermissionRule rule = PermissionRule.parse("Read", ToolPermission.ALLOWED);
        assertThat(rule.toolPattern()).isEqualTo("read");
        assertThat(rule.argGlob()).isNull();
        assertThat(rule.permission()).isEqualTo(ToolPermission.ALLOWED);
    }

    @Test
    void parseToolNameWithGlob() {
        PermissionRule rule = PermissionRule.parse("Bash(npm test*)", ToolPermission.ALLOWED);
        assertThat(rule.toolPattern()).isEqualTo("bash");
        assertThat(rule.argGlob()).isEqualTo("npm test*");
        assertThat(rule.permission()).isEqualTo(ToolPermission.ALLOWED);
    }

    @Test
    void parseToolNameGlobPattern() {
        PermissionRule rule = PermissionRule.parse("mcp_*", ToolPermission.ASK);
        assertThat(rule.toolPattern()).isEqualTo("mcp_*");
        assertThat(rule.argGlob()).isNull();
    }

    @Test
    void parseWildcard() {
        PermissionRule rule = PermissionRule.parse("*", ToolPermission.DENIED);
        assertThat(rule.toolPattern()).isEqualTo("*");
    }

    @Test
    void parseRejectsEmpty() {
        assertThatThrownBy(() -> PermissionRule.parse("", ToolPermission.ALLOWED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseRejectsMalformedParen() {
        assertThatThrownBy(() -> PermissionRule.parse("Bash(npm test", ToolPermission.ALLOWED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closing paren");
    }

    // -- PermissionRule.matches --

    @Test
    void matchesExactToolName() {
        PermissionRule rule = PermissionRule.parse("bash", ToolPermission.ALLOWED);
        assertThat(rule.matches("bash", Map.of())).isTrue();
        assertThat(rule.matches("Bash", Map.of())).isTrue();
        assertThat(rule.matches("read", Map.of())).isFalse();
    }

    @Test
    void matchesToolGlob() {
        PermissionRule rule = PermissionRule.parse("mcp_*", ToolPermission.ASK);
        assertThat(rule.matches("mcp_github", Map.of())).isTrue();
        assertThat(rule.matches("mcp_slack", Map.of())).isTrue();
        assertThat(rule.matches("bash", Map.of())).isFalse();
    }

    @Test
    void matchesWildcardAll() {
        PermissionRule rule = PermissionRule.parse("*", ToolPermission.ASK);
        assertThat(rule.matches("bash", Map.of())).isTrue();
        assertThat(rule.matches("read", Map.of())).isTrue();
        assertThat(rule.matches("anything", Map.of())).isTrue();
    }

    @Test
    void matchesArgGlobCommand() {
        PermissionRule rule = PermissionRule.parse("Bash(npm test*)", ToolPermission.ALLOWED);
        assertThat(rule.matches("bash", Map.of("command", "npm test --coverage"))).isTrue();
        assertThat(rule.matches("bash", Map.of("command", "npm run build"))).isFalse();
        assertThat(rule.matches("bash", Map.of())).isFalse();
    }

    @Test
    void matchesArgGlobFilePath() {
        PermissionRule rule = PermissionRule.parse("Write(src/**/*.java)", ToolPermission.ALLOWED);
        assertThat(rule.matches("write", Map.of("file_path", "src/main/App.java"))).isTrue();
        assertThat(rule.matches("write", Map.of("file_path", "src/test/FooTest.java"))).isTrue();
        assertThat(rule.matches("write", Map.of("file_path", "/etc/passwd"))).isFalse();
    }

    @Test
    void matchesPrimaryArgExtraction() {
        PermissionRule rule = PermissionRule.parse("Bash(git *)", ToolPermission.ASK);
        assertThat(rule.matches("bash", Map.of("command", "git push"))).isTrue();
        assertThat(rule.matches("bash", Map.of("other_key", "git push"))).isFalse();
    }

    @Test
    void matchesFilePathKey() {
        PermissionRule rule = PermissionRule.parse("edit(*.md)", ToolPermission.ALLOWED);
        assertThat(rule.matches("edit", Map.of("filePath", "README.md"))).isTrue();
        assertThat(rule.matches("edit", Map.of("path", "docs/guide.md"))).isTrue();
    }

    // -- PermissionRuleEngine.resolve --

    @Test
    void emptyRulesReturnsEmpty() {
        PermissionRuleEngine engine = new PermissionRuleEngine(List.of());
        assertThat(engine.resolve("bash", Map.of("command", "ls"))).isEmpty();
    }

    @Test
    void firstMatchWins() {
        PermissionRuleEngine engine =
                new PermissionRuleEngine(
                        List.of(
                                PermissionRule.parse("Bash(npm test*)", ToolPermission.ALLOWED),
                                PermissionRule.parse("Bash", ToolPermission.ASK)));

        assertThat(engine.resolve("bash", Map.of("command", "npm test")))
                .isEqualTo(Optional.of(ToolPermission.ALLOWED));
        assertThat(engine.resolve("bash", Map.of("command", "rm -rf /")))
                .isEqualTo(Optional.of(ToolPermission.ASK));
    }

    @Test
    void denyBeforeAllow() {
        PermissionRuleEngine engine =
                new PermissionRuleEngine(
                        List.of(
                                PermissionRule.parse("Bash(rm -rf*)", ToolPermission.DENIED),
                                PermissionRule.parse("Bash(*)", ToolPermission.ALLOWED)));

        assertThat(engine.resolve("bash", Map.of("command", "rm -rf /")))
                .isEqualTo(Optional.of(ToolPermission.DENIED));
        assertThat(engine.resolve("bash", Map.of("command", "ls")))
                .isEqualTo(Optional.of(ToolPermission.ALLOWED));
    }

    @Test
    void noMatchFallsThrough() {
        PermissionRuleEngine engine =
                new PermissionRuleEngine(
                        List.of(PermissionRule.parse("Bash(npm *)", ToolPermission.ALLOWED)));

        assertThat(engine.resolve("read", Map.of())).isEmpty();
    }

    @Test
    void ruleCount() {
        PermissionRuleEngine engine =
                new PermissionRuleEngine(
                        List.of(
                                PermissionRule.parse("Read", ToolPermission.ALLOWED),
                                PermissionRule.parse("Write", ToolPermission.ASK)));
        assertThat(engine.ruleCount()).isEqualTo(2);
    }

    // -- Glob matching edge cases --

    @Test
    void globMatchesCaseInsensitive() {
        assertThat(PermissionRule.globMatches("bash", "BASH")).isTrue();
        assertThat(PermissionRule.globMatches("Bash", "bash")).isTrue();
    }

    @Test
    void globMatchesQuestionMark() {
        assertThat(PermissionRule.globMatches("tes?", "test")).isTrue();
        assertThat(PermissionRule.globMatches("tes?", "tess")).isTrue();
        assertThat(PermissionRule.globMatches("tes?", "tests")).isFalse();
    }

    @Test
    void globMatchesDoubleStarPath() {
        assertThat(PermissionRule.globMatches("src/**/*.java", "src/main/java/App.java")).isTrue();
        assertThat(PermissionRule.globMatches("src/**/*.java", "src/App.java")).isTrue();
    }

    @Test
    void globEscapesSpecialChars() {
        assertThat(PermissionRule.globMatches("file.txt", "file.txt")).isTrue();
        assertThat(PermissionRule.globMatches("file.txt", "fileatxt")).isFalse();
    }
}
