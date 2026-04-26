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
        run("git", "init");
        run("git", "config", "user.email", "test@kairo.io");
        run("git", "config", "user.name", "Test");
        tool = new GitTool(repoDir);
    }

    // --- Basic operations ---

    @Test
    void statusInEmptyRepo() {
        ToolResult result = tool.execute(Map.of("subcommand", "status"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).containsIgnoringCase("nothing to commit");
    }

    @Test
    void logOnEmptyRepoReturnsNonFatalError() {
        ToolResult result = tool.execute(Map.of("subcommand", "log --oneline"));
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
    void missingSubcommandReturnsError() {
        ToolResult result = tool.execute(Map.of());
        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("'subcommand' is required");
    }

    // --- Dangerous operation guards ---

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

    // --- Extended: add / commit / branch / checkout ---

    @Test
    void addStagedFilesShownInStatus() throws IOException {
        Files.writeString(repoDir.resolve("new.txt"), "data");

        ToolResult addResult = tool.execute(Map.of("subcommand", "add new.txt"));
        assertThat(addResult.isError()).isFalse();

        ToolResult statusResult = tool.execute(Map.of("subcommand", "status"));
        assertThat(statusResult.content()).containsIgnoringCase("new.txt");
    }

    @Test
    void addAndCommitSucceeds() throws IOException {
        Files.writeString(repoDir.resolve("data.txt"), "hello world");
        tool.execute(Map.of("subcommand", "add data.txt"));

        ToolResult commitResult =
                tool.execute(Map.of("subcommand", "commit -m \"feat: add data file\""));
        assertThat(commitResult.isError()).isFalse();
        assertThat(commitResult.content()).containsIgnoringCase("data file");
    }

    @Test
    void createBranchSucceeds() throws IOException, InterruptedException {
        Files.writeString(repoDir.resolve("base.txt"), "base");
        run("git", "add", ".");
        run("git", "commit", "-m", "base commit");

        ToolResult result = tool.execute(Map.of("subcommand", "branch feature-x"));
        assertThat(result.isError()).isFalse();

        ToolResult branches = tool.execute(Map.of("subcommand", "branch"));
        assertThat(branches.content()).contains("feature-x");
    }

    @Test
    void checkoutNewBranchSucceeds() throws IOException, InterruptedException {
        Files.writeString(repoDir.resolve("init.txt"), "init");
        run("git", "add", ".");
        run("git", "commit", "-m", "initial");

        ToolResult result = tool.execute(Map.of("subcommand", "checkout -b new-feature"));
        assertThat(result.isError()).isFalse();

        ToolResult status = tool.execute(Map.of("subcommand", "status"));
        assertThat(status.content()).contains("new-feature");
    }

    @Test
    void diffStagedShowsStagedChanges() throws IOException, InterruptedException {
        Files.writeString(repoDir.resolve("staged.txt"), "original");
        run("git", "add", ".");
        run("git", "commit", "-m", "base");
        Files.writeString(repoDir.resolve("staged.txt"), "modified");
        tool.execute(Map.of("subcommand", "add staged.txt"));

        ToolResult result = tool.execute(Map.of("subcommand", "diff --staged"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("modified");
    }

    @Test
    void statusPorcelainMachineReadable() throws IOException {
        Files.writeString(repoDir.resolve("untracked.txt"), "new file");

        ToolResult result = tool.execute(Map.of("subcommand", "status --porcelain"));
        assertThat(result.isError()).isFalse();
        // Porcelain format: "??" prefix for untracked files
        assertThat(result.content()).contains("??").contains("untracked.txt");
    }

    @Test
    void logOnelineAfterMultipleCommits() throws IOException, InterruptedException {
        Files.writeString(repoDir.resolve("a.txt"), "a");
        run("git", "add", ".");
        run("git", "commit", "-m", "commit-a");
        Files.writeString(repoDir.resolve("b.txt"), "b");
        run("git", "add", ".");
        run("git", "commit", "-m", "commit-b");

        ToolResult result = tool.execute(Map.of("subcommand", "log --oneline"));
        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("commit-a").contains("commit-b");
    }

    private void run(String... cmd) throws IOException, InterruptedException {
        new ProcessBuilder(cmd).directory(repoDir.toFile()).inheritIO().start().waitFor();
    }
}
