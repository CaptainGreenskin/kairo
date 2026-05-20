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
package io.kairo.core.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.kairo.api.guardrail.*;
import io.kairo.api.tool.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ToolSafetyGuardrailPolicyTest {

    private ToolRegistry registry;
    private ToolSafetyGuardrailPolicy policy;

    @BeforeEach
    void setUp() {
        registry = mock(ToolRegistry.class);
        policy = new ToolSafetyGuardrailPolicy(registry);
    }

    private ToolDefinition toolDef(String name, ToolSideEffect sideEffect) {
        return new ToolDefinition(
                name,
                "test tool",
                ToolCategory.EXECUTION,
                new JsonSchema("object", null, null, ""),
                Object.class,
                null,
                sideEffect);
    }

    private GuardrailContext preToolContext(
            String toolName, Map<String, Object> args, Map<String, Object> metadata) {
        return new GuardrailContext(
                GuardrailPhase.PRE_TOOL,
                "test-agent",
                toolName,
                new GuardrailPayload.ToolInput(toolName, args),
                metadata);
    }

    // ── WRITE tool + path outside workspace = DENY ──────────────────────────────

    @Test
    @DisplayName("WRITE tool with path outside workspace is denied")
    void writeToolPathOutsideWorkspace_deny() {
        when(registry.get("write-file"))
                .thenReturn(Optional.of(toolDef("write-file", ToolSideEffect.WRITE)));

        Map<String, Object> args = Map.of("filePath", "../../../etc/passwd");
        Map<String, Object> metadata = Map.of("workspace.root", Path.of("/tmp/project"));

        GuardrailContext ctx = preToolContext("write-file", args, metadata);

        StepVerifier.create(policy.evaluate(ctx))
                .assertNext(
                        decision ->
                                assertThat(decision.action())
                                        .isEqualTo(GuardrailDecision.Action.DENY))
                .verifyComplete();
    }

    // ── WRITE tool + path inside workspace = ALLOW ──────────────────────────────

    @Test
    @DisplayName("WRITE tool with path inside workspace is allowed")
    void writeToolPathInsideWorkspace_allow() {
        when(registry.get("write-file"))
                .thenReturn(Optional.of(toolDef("write-file", ToolSideEffect.WRITE)));

        Map<String, Object> args = Map.of("filePath", "src/Main.java");
        Map<String, Object> metadata = Map.of("workspace.root", Path.of("/tmp/project"));

        GuardrailContext ctx = preToolContext("write-file", args, metadata);

        StepVerifier.create(policy.evaluate(ctx))
                .assertNext(
                        decision ->
                                assertThat(decision.action())
                                        .isEqualTo(GuardrailDecision.Action.ALLOW))
                .verifyComplete();
    }

    // ── SYSTEM_CHANGE + catastrophic cmd = DENY ─────────────────────────────────

    @Test
    @DisplayName("SYSTEM_CHANGE tool with catastrophic command is denied")
    void systemChangeToolCatastrophicCommand_deny() {
        when(registry.get("bash"))
                .thenReturn(Optional.of(toolDef("bash", ToolSideEffect.SYSTEM_CHANGE)));

        Map<String, Object> args = Map.of("command", "rm -rf /");
        Map<String, Object> metadata = new HashMap<>();

        GuardrailContext ctx = preToolContext("bash", args, metadata);

        StepVerifier.create(policy.evaluate(ctx))
                .assertNext(
                        decision ->
                                assertThat(decision.action())
                                        .isEqualTo(GuardrailDecision.Action.DENY))
                .verifyComplete();
    }

    // ── SYSTEM_CHANGE + safe cmd = ALLOW ────────────────────────────────────────

    @Test
    @DisplayName("SYSTEM_CHANGE tool with safe command is allowed")
    void systemChangeToolSafeCommand_allow() {
        when(registry.get("bash"))
                .thenReturn(Optional.of(toolDef("bash", ToolSideEffect.SYSTEM_CHANGE)));

        Map<String, Object> args = Map.of("command", "ls -la");
        Map<String, Object> metadata = new HashMap<>();

        GuardrailContext ctx = preToolContext("bash", args, metadata);

        StepVerifier.create(policy.evaluate(ctx))
                .assertNext(
                        decision ->
                                assertThat(decision.action())
                                        .isEqualTo(GuardrailDecision.Action.ALLOW))
                .verifyComplete();
    }

    // ── SYSTEM_CHANGE + dangerous (Tier 2) cmd = ALLOW ──────────────────────────

    @Test
    @DisplayName(
            "SYSTEM_CHANGE tool with dangerous (Tier 2) command is ALLOWED — guardrail does not enforce Tier 2")
    void systemChangeToolDangerousCommand_allow() {
        when(registry.get("bash"))
                .thenReturn(Optional.of(toolDef("bash", ToolSideEffect.SYSTEM_CHANGE)));

        Map<String, Object> args = Map.of("command", "git push --force");
        Map<String, Object> metadata = new HashMap<>();

        GuardrailContext ctx = preToolContext("bash", args, metadata);

        StepVerifier.create(policy.evaluate(ctx))
                .assertNext(
                        decision ->
                                assertThat(decision.action())
                                        .isEqualTo(GuardrailDecision.Action.ALLOW))
                .verifyComplete();
    }

    // ── READ_ONLY tool = ALLOW ──────────────────────────────────────────────────

    @Test
    @DisplayName("READ_ONLY tool is always allowed regardless of args")
    void readOnlyTool_allow() {
        when(registry.get("read-file"))
                .thenReturn(Optional.of(toolDef("read-file", ToolSideEffect.READ_ONLY)));

        Map<String, Object> args = Map.of("filePath", "../../../etc/passwd");
        Map<String, Object> metadata = Map.of("workspace.root", Path.of("/tmp/project"));

        GuardrailContext ctx = preToolContext("read-file", args, metadata);

        StepVerifier.create(policy.evaluate(ctx))
                .assertNext(
                        decision ->
                                assertThat(decision.action())
                                        .isEqualTo(GuardrailDecision.Action.ALLOW))
                .verifyComplete();
    }

    // ── Missing workspace.root = skip boundary check ────────────────────────────

    @Test
    @DisplayName("WRITE tool without workspace.root in metadata skips boundary check and allows")
    void writeToolMissingWorkspaceRoot_allow() {
        when(registry.get("write-file"))
                .thenReturn(Optional.of(toolDef("write-file", ToolSideEffect.WRITE)));

        Map<String, Object> args = Map.of("filePath", "../../../etc/passwd");
        Map<String, Object> metadata = new HashMap<>(); // no workspace.root

        GuardrailContext ctx = preToolContext("write-file", args, metadata);

        StepVerifier.create(policy.evaluate(ctx))
                .assertNext(
                        decision ->
                                assertThat(decision.action())
                                        .isEqualTo(GuardrailDecision.Action.ALLOW))
                .verifyComplete();
    }

    // ── PRE_MODEL phase = ALLOW (wrong phase) ───────────────────────────────────

    @Test
    @DisplayName("PRE_MODEL phase is skipped — all checks bypassed")
    void preModelPhase_allow() {
        GuardrailContext ctx =
                new GuardrailContext(
                        GuardrailPhase.PRE_MODEL,
                        "test-agent",
                        "bash",
                        new GuardrailPayload.ToolInput("bash", Map.of("command", "rm -rf /")),
                        Map.of("workspace.root", Path.of("/tmp/project")));

        StepVerifier.create(policy.evaluate(ctx))
                .assertNext(
                        decision ->
                                assertThat(decision.action())
                                        .isEqualTo(GuardrailDecision.Action.ALLOW))
                .verifyComplete();
    }
}
