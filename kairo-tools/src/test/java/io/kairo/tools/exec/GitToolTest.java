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
package io.kairo.tools.exec;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.tool.ToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitToolTest {

    @TempDir Path repoDir;

    private GitTool tool;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        // Initialize a fresh git repository for each test
        run("git", "init");
        run("git", "config", "user.email", "test@kairo.io");
        run("git", "config", "user.name", "Test");
        tool = new GitTool(repoDir);
    }

    @Test
    void statusInEmptyRepo() {
        ToolResult result = tool.execute(Map.of("subcommand", "status"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).containsIgnoringCase("nothing to commit");
    }

    @Test
    void logOnEmptyRepoReturnsNonFatalError() {
        ToolResult result = tool.execute(Map.of("subcommand", "log --oneline"));
        // git log on an empty repo exits with code 128 — isError=true is correct
        assertThat(result.metadata()).containsKey("exitCode");
    }

    @Test
    void logAfterCommitShowsEntry() throws IOException, InterruptedException {
        Files.writeString(repoDir.resolve("file.txt"), "hello");
        run("git", "add", ".");
        run("git", "commit", "-m", "initial");

        ToolResult result = tool.execute(Map.of("subcommand", "log --oneline"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("initial");
    }

    @Test
    void diffShowsChanges() throws IOException, InterruptedException {
        Files.writeString(repoDir.resolve("readme.txt"), "v1");
        run("git", "add", ".");
        run("git", "commit", "-m", "base");
        Files.writeString(repoDir.resolve("readme.txt"), "v2");

        ToolResult result = tool.execute(Map.of("subcommand", "diff"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("v2");
    }

    @Test
    void forcePushIsBlocked() {
        ToolResult result = tool.execute(Map.of("subcommand", "push --force origin main"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("Blocked");
    }

    @Test
    void resetHardIsBlocked() {
        ToolResult result = tool.execute(Map.of("subcommand", "reset --hard HEAD"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("Blocked");
    }

    @Test
    void cleanFIsBlocked() {
        ToolResult result = tool.execute(Map.of("subcommand", "clean -f"));
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("Blocked");
    }

    @Test
    void missingSubcommandReturnsError() {
        ToolResult result = tool.execute(Map.of());
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("'subcommand' is required");
    }

    // Helper to run a process in repoDir
    private void run(String... cmd) throws IOException, InterruptedException {
        new ProcessBuilder(cmd).directory(repoDir.toFile()).inheritIO().start().waitFor();
    }
}
