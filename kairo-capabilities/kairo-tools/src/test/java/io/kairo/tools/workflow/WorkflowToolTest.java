/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.tools.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.tenant.TenantContext;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolInvocation;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.workspace.Workspace;
import io.kairo.api.workspace.WorkspaceKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class WorkflowToolTest {

    @Test
    void missingToolExecutor_returnsError(@TempDir Path dir) {
        WorkflowTool tool = new WorkflowTool();
        ToolResult r = tool.execute(Map.of("name", "any"), ctxFor(dir)).block();
        assertThat(r.isError()).isTrue();
        assertThat(r.content()).contains("setToolExecutor");
    }

    @Test
    void missingNameParam_returnsError(@TempDir Path dir) {
        WorkflowTool tool = new WorkflowTool();
        tool.setToolExecutor(new RecordingExecutor());
        ToolResult r = tool.execute(Map.of(), ctxFor(dir)).block();
        assertThat(r.isError()).isTrue();
        assertThat(r.content()).contains("'name' is required");
    }

    @Test
    void workflowFileNotFound_returnsError(@TempDir Path dir) {
        WorkflowTool tool = new WorkflowTool();
        tool.setToolExecutor(new RecordingExecutor());
        ToolResult r = tool.execute(Map.of("name", "ghost"), ctxFor(dir)).block();
        assertThat(r.isError()).isTrue();
        assertThat(r.content()).contains("not found");
    }

    @Test
    void executesAllStepsInOrder(@TempDir Path dir) throws Exception {
        writeWorkflow(
                dir,
                "ordered",
                """
                name: ordered
                steps:
                  - name: first
                    tool: a
                    args: { msg: hi }
                  - name: second
                    tool: b
                """);
        RecordingExecutor exec = new RecordingExecutor();
        exec.respond("a", ToolResult.success("a-id", "a-out", Map.of()));
        exec.respond("b", ToolResult.success("b-id", "b-out", Map.of()));

        WorkflowTool tool = new WorkflowTool();
        tool.setToolExecutor(exec);
        ToolResult r = tool.execute(Map.of("name", "ordered"), ctxFor(dir)).block();

        assertThat(r.isError()).isFalse();
        assertThat(exec.calls).extracting("toolName").containsExactly("a", "b");
        assertThat(exec.calls.get(0).input()).containsEntry("msg", "hi");
        assertThat(r.metadata().get("completedSteps")).isEqualTo(2);
        assertThat(r.metadata().get("aborted")).isEqualTo(false);
        assertThat(r.content()).contains("COMPLETED");
    }

    @Test
    void abortsOnFailureByDefault(@TempDir Path dir) throws Exception {
        writeWorkflow(
                dir,
                "fail",
                """
                name: fail
                steps:
                  - name: ok
                    tool: a
                  - name: bad
                    tool: b
                  - name: never
                    tool: c
                """);
        RecordingExecutor exec = new RecordingExecutor();
        exec.respond("a", ToolResult.success("1", "ok", Map.of()));
        exec.respond("b", ToolResult.error("2", "boom"));
        exec.respond("c", ToolResult.success("3", "should not run", Map.of()));

        WorkflowTool tool = new WorkflowTool();
        tool.setToolExecutor(exec);
        ToolResult r = tool.execute(Map.of("name", "fail"), ctxFor(dir)).block();

        // Third step must not be invoked — abort kicks in after the second.
        assertThat(exec.calls).extracting("toolName").containsExactly("a", "b");
        assertThat(r.metadata().get("aborted")).isEqualTo(true);
        assertThat(r.metadata().get("completedSteps")).isEqualTo(1);
        assertThat(r.metadata().get("failedSteps")).isEqualTo(1);
        assertThat(r.content()).contains("ABORTED");
    }

    @Test
    void continueOnError_keepsGoing(@TempDir Path dir) throws Exception {
        writeWorkflow(
                dir,
                "tolerant",
                """
                name: tolerant
                steps:
                  - name: best-effort
                    tool: a
                    continue_on_error: true
                  - name: must-run
                    tool: b
                """);
        RecordingExecutor exec = new RecordingExecutor();
        exec.respond("a", ToolResult.error("1", "intentional"));
        exec.respond("b", ToolResult.success("2", "yes", Map.of()));

        WorkflowTool tool = new WorkflowTool();
        tool.setToolExecutor(exec);
        ToolResult r = tool.execute(Map.of("name", "tolerant"), ctxFor(dir)).block();

        assertThat(exec.calls).extracting("toolName").containsExactly("a", "b");
        assertThat(r.metadata().get("aborted")).isEqualTo(false);
        assertThat(r.metadata().get("completedSteps")).isEqualTo(1);
        assertThat(r.metadata().get("failedSteps")).isEqualTo(1);
        // No "ABORTED" marker — completed-with-failures is the right signal.
        assertThat(r.content()).contains("COMPLETED_WITH_FAILURES");
    }

    @Test
    void resolvesYmlExtension(@TempDir Path dir) throws Exception {
        Path wfDir = Files.createDirectories(dir.resolve(".kairo/workflows"));
        Files.writeString(
                wfDir.resolve("yml-form.yml"), "name: yf\nsteps:\n  - name: s\n    tool: a\n");
        RecordingExecutor exec = new RecordingExecutor();
        exec.respond("a", ToolResult.success("1", "ok", Map.of()));
        WorkflowTool tool = new WorkflowTool();
        tool.setToolExecutor(exec);

        ToolResult r = tool.execute(Map.of("name", "yml-form"), ctxFor(dir)).block();
        assertThat(r.isError()).isFalse();
    }

    @Test
    void absolutePath_isAccepted(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("custom.yaml");
        Files.writeString(file, "name: abs\nsteps:\n  - name: s\n    tool: a\n");
        RecordingExecutor exec = new RecordingExecutor();
        exec.respond("a", ToolResult.success("1", "ok", Map.of()));
        WorkflowTool tool = new WorkflowTool();
        tool.setToolExecutor(exec);

        ToolResult r = tool.execute(Map.of("name", file.toString()), ctxFor(dir)).block();
        assertThat(r.isError()).isFalse();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static void writeWorkflow(Path dir, String name, String yaml) throws Exception {
        Path wfDir = Files.createDirectories(dir.resolve(".kairo/workflows"));
        Files.writeString(wfDir.resolve(name + ".yaml"), yaml);
    }

    private static ToolContext ctxFor(Path dir) {
        return new ToolContext(
                "agent-1",
                "sess-1",
                Map.of(),
                null,
                TenantContext.SINGLE,
                new TempDirWorkspace(dir));
    }

    /** Stub ToolExecutor that records each call + returns canned responses by tool name. */
    private static final class RecordingExecutor implements ToolExecutor {
        final List<ToolInvocation> calls = new ArrayList<>();
        final Map<String, ToolResult> responses = new LinkedHashMap<>();

        void respond(String tool, ToolResult result) {
            responses.put(tool, result);
        }

        @Override
        public Mono<ToolResult> execute(String toolName, Map<String, Object> input) {
            calls.add(new ToolInvocation(toolName, input, "call-" + calls.size()));
            ToolResult r =
                    responses.getOrDefault(
                            toolName, ToolResult.error("?", "no canned response for " + toolName));
            return Mono.just(r);
        }

        @Override
        public Mono<ToolResult> execute(
                String toolName, Map<String, Object> input, Duration timeout) {
            return execute(toolName, input);
        }

        @Override
        public Flux<ToolResult> executeParallel(List<ToolInvocation> invocations) {
            return Flux.fromIterable(invocations)
                    .flatMap(inv -> execute(inv.toolName(), inv.input()));
        }
    }

    private record TempDirWorkspace(Path root) implements Workspace {
        @Override
        public String id() {
            return "test";
        }

        @Override
        public WorkspaceKind kind() {
            return WorkspaceKind.LOCAL;
        }

        @Override
        public Map<String, String> metadata() {
            return Map.of();
        }
    }
}
