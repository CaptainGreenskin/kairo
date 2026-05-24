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
package io.kairo.expertteam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kairo.api.team.EvaluationContext;
import io.kairo.api.team.EvaluationVerdict.VerdictOutcome;
import io.kairo.api.team.RoleDefinition;
import io.kairo.api.team.TeamConfig;
import io.kairo.api.team.TeamStep;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

class PatchValidationStrategyTest {

    private static final RoleDefinition DUMMY_ROLE =
            new RoleDefinition("test-role", "Test Role", "A test role", "agent.default", List.of());

    private static final TeamStep DUMMY_STEP =
            new TeamStep("step-1", "Do something", DUMMY_ROLE, List.of(), 0);

    private static final TeamConfig DUMMY_CONFIG = TeamConfig.defaults();

    private EvaluationContext createContext(int attemptNumber) {
        return new EvaluationContext(
                DUMMY_STEP, "some artifact text", attemptNumber, List.of(), DUMMY_CONFIG);
    }

    @Test
    void nonEmptyDiff_passes(@TempDir Path tmp) throws Exception {
        Path repo = initRepoWithCommit(tmp);
        Files.writeString(repo.resolve("hello.txt"), "modified content");

        PatchValidationStrategy strategy = new PatchValidationStrategy(repo);

        StepVerifier.create(strategy.evaluate(createContext(1)))
                .assertNext(
                        v -> {
                            assertEquals(VerdictOutcome.PASS, v.outcome());
                            assertEquals(1.0, v.score());
                            assertTrue(v.feedback().contains("Patch detected"));
                        })
                .verifyComplete();
    }

    @Test
    void emptyDiff_requestsRevision(@TempDir Path tmp) throws Exception {
        Path repo = initRepoWithCommit(tmp);

        PatchValidationStrategy strategy = new PatchValidationStrategy(repo);

        StepVerifier.create(strategy.evaluate(createContext(1)))
                .assertNext(
                        v -> {
                            assertEquals(VerdictOutcome.REVISE, v.outcome());
                            assertEquals(0.0, v.score());
                            assertTrue(v.feedback().contains("No code changes detected"));
                            assertTrue(
                                    v.suggestions().stream()
                                            .anyMatch(s -> s.contains("edit/write tool")));
                        })
                .verifyComplete();
    }

    @Test
    void notAGitRepo_reviewExceeded(@TempDir Path tmp) {
        PatchValidationStrategy strategy = new PatchValidationStrategy(tmp);

        StepVerifier.create(strategy.evaluate(createContext(1)))
                .assertNext(
                        v -> {
                            assertEquals(VerdictOutcome.REVIEW_EXCEEDED, v.outcome());
                            assertEquals(0.0, v.score());
                            assertTrue(v.feedback().contains("Git validation failed"));
                        })
                .verifyComplete();
    }

    @Test
    void multipleFileChanges_summaryInFeedback(@TempDir Path tmp) throws Exception {
        Path repo = initRepoWithCommit(tmp);
        // Modify tracked file + add and track new files, then modify them
        Files.writeString(repo.resolve("hello.txt"), "modified");
        Files.writeString(repo.resolve("extra.txt"), "extra content");
        runCommand(repo, "git", "add", "extra.txt");
        runCommand(
                repo,
                "git",
                "-c",
                "user.name=test",
                "-c",
                "user.email=test@test.com",
                "commit",
                "-m",
                "add extra");
        // Now modify both tracked files
        Files.writeString(repo.resolve("hello.txt"), "changed again");
        Files.writeString(repo.resolve("extra.txt"), "modified extra");

        PatchValidationStrategy strategy = new PatchValidationStrategy(repo);

        StepVerifier.create(strategy.evaluate(createContext(1)))
                .assertNext(
                        v -> {
                            assertEquals(VerdictOutcome.PASS, v.outcome());
                            assertTrue(v.feedback().contains("2 file"));
                        })
                .verifyComplete();
    }

    @Test
    void workspaceAccessor() {
        Path ws = Path.of("/tmp/test-workspace");
        PatchValidationStrategy strategy = new PatchValidationStrategy(ws);
        assertEquals(ws, strategy.workspace());
    }

    private Path initRepoWithCommit(Path parent) throws IOException, InterruptedException {
        Path repo = parent.resolve("repo");
        Files.createDirectories(repo);
        runCommand(repo, "git", "init");
        Files.writeString(repo.resolve("hello.txt"), "initial content");
        runCommand(repo, "git", "add", "hello.txt");
        runCommand(
                repo,
                "git",
                "-c",
                "user.name=test",
                "-c",
                "user.email=test@test.com",
                "commit",
                "-m",
                "initial");
        return repo;
    }

    private void runCommand(Path dir, String... cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        p.getInputStream().transferTo(System.err);
        int exit = p.waitFor();
        if (exit != 0) {
            throw new RuntimeException(
                    "Command failed (exit=" + exit + "): " + String.join(" ", cmd));
        }
    }
}
