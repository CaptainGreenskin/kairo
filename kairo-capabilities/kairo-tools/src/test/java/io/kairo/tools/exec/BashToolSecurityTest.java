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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kairo.api.sandbox.ExecutionSandbox;
import io.kairo.api.sandbox.SandboxExit;
import io.kairo.api.sandbox.SandboxHandle;
import io.kairo.api.sandbox.SandboxOutputChunk;
import io.kairo.api.sandbox.SandboxRequest;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolEvent;
import io.kairo.api.tool.ToolOutcome;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.workspace.Workspace;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Security-focused tests for {@link BashTool}.
 *
 * <p>These tests validate the 3-tier command safety model:
 *
 * <ul>
 *   <li>Tier 1 (Catastrophic) — hard-blocked by {@code checkCatastrophic()}
 *   <li>Tier 2 (Dangerous) — approval-gated via {@code isDangerous()} + ApprovalGate
 *   <li>Tier 3 (Safe) — executed normally
 * </ul>
 *
 * <p>Also validates injection protection, sandbox boundaries, timeout enforcement, and output
 * safety. Mocks the {@link ExecutionSandbox} SPI where needed.
 */
class BashToolSecurityTest {

    private BashTool tool;
    private ExecutionSandbox mockSandbox;
    private ToolContext mockContext;

    private static final ToolContext CTX = new ToolContext("agent-1", "sess-1", Map.of());

    private ToolResult exec(Map<String, Object> args) {
        return exec(args, CTX);
    }

    private ToolResult exec(Map<String, Object> args, ToolContext ctx) {
        return tool.stream(args, ctx)
                .filter(e -> e instanceof ToolEvent.Final)
                .map(e -> ((ToolEvent.Final) e).result())
                .blockLast();
    }

    @BeforeEach
    void setUp() {
        tool = new BashTool();
        mockSandbox = mock(ExecutionSandbox.class);
        mockContext = mock(ToolContext.class);
        when(mockContext.getBean(ExecutionSandbox.class)).thenReturn(Optional.of(mockSandbox));
        when(mockContext.tenant()).thenReturn(io.kairo.api.tenant.TenantContext.SINGLE);
        when(mockContext.workspace()).thenReturn(Workspace.cwd());
    }

    // --- Tier 1: Catastrophic commands (hard-blocked) ---

    @ParameterizedTest
    @ValueSource(
            strings = {
                "rm -rf /",
                "rm -rf /*",
                "rm -rf ~/",
                "rm -rf .git",
                "mkfs /dev/sda",
                "dd if=/dev/zero of=/dev/sda",
                "shred /dev/sda",
                "wipefs /dev/sda"
            })
    void catastrophicCommandsAreHardBlocked(String cmd) {
        ToolResult result = exec(Map.of("command", cmd));
        assertTrue(result.isError(), "Should block catastrophic: " + cmd);
        assertTrue(
                result.content().contains("Blocked"),
                "Error should contain 'Blocked': " + result.content());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sudo apt-get install vim", "su - root", "doas reboot"})
    void blockedElevatedPrivilegeCommands(String cmd) {
        ToolResult result = exec(Map.of("command", cmd));
        assertTrue(result.isError(), "Should block: " + cmd);
        assertTrue(result.content().contains("elevated privileges"));
    }

    @Test
    void catastrophicCommandInChainWithSemicolon() {
        ToolResult result = exec(Map.of("command", "echo safe; rm -rf /"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("Blocked"));
    }

    @Test
    void sudoInChainWithSemicolon() {
        ToolResult result = exec(Map.of("command", "echo safe; sudo reboot"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("Blocked"));
    }

    @Test
    void catastrophicCommandOutcomeIsError() {
        ToolResult result = exec(Map.of("command", "rm -rf /"));
        assertTrue(result.isError());
        assertEquals(ToolOutcome.ERROR, result.outcome());
    }

    // --- Tier 2: Dangerous commands (approval-gated, not hard-blocked) ---

    @ParameterizedTest
    @ValueSource(
            strings = {
                "rm -rf /etc/nginx",
                "chmod 777 /var/www",
                "shutdown -h now",
                "git push --force origin main"
            })
    void dangerousCommandsAreNotHardBlocked(String cmd) {
        ToolResult result = exec(Map.of("command", cmd));
        assertFalse(
                result.content().contains("Blocked"),
                "Dangerous command should not be hard-blocked: " + cmd);
    }

    // --- Tier 3: Safe commands (no longer blocked) ---
    // NOTE: paths use /tmp/nonexist_ prefix to avoid harming the build during test execution

    @ParameterizedTest
    @ValueSource(
            strings = {
                "rm /tmp/nonexist_file.java",
                "rm -rf /tmp/nonexist_target/",
                "rm -rf /tmp/nonexist_node_modules/",
                "rmdir /tmp/nonexist_empty_dir",
                "chmod +x /tmp/nonexist_script.sh",
                "chmod 644 /tmp/nonexist_file.txt",
                "chown user:group /tmp/nonexist_file.txt",
                "find /tmp/nonexist_dir -name '*.class' -delete",
                "find /tmp/nonexist_dir -exec rm {} \\;",
                "echo safe | xargs echo rm",
                "mv /tmp/nonexist_file1 /tmp/nonexist_file2",
                "echo hello && rm -rf /tmp/nonexist_data"
            })
    void previouslyOverBlockedCommandsAreNowAllowed(String cmd) {
        ToolResult result = exec(Map.of("command", cmd));
        assertFalse(
                result.content().contains("Blocked"),
                "Should NOT be blocked (was over-blocked before): "
                        + cmd
                        + ", got: "
                        + result.content());
    }

    @Test
    void rmFileIsNotBlocked() {
        ToolResult result = exec(Map.of("command", "rm /tmp/nonexist_file.java"));
        assertFalse(
                result.content().contains("Blocked"),
                "rm file.java must not be blocked — this is the original bug");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "ls -la",
                "cat /dev/null",
                "echo hello world",
                "git rm --dry-run file.txt",
                "grep -r 'pattern' /dev/null",
                "find /tmp -maxdepth 0 -name '*.java' -type f",
                "ps aux",
                "pwd"
            })
    void allowedSafeCommands(String cmd) {
        ToolResult result = exec(Map.of("command", cmd));
        assertFalse(
                result.content().contains("Blocked"),
                "Should not block: " + cmd + ", got: " + result.content());
    }

    @Test
    void gitRmIsNotBlocked() {
        ToolResult result = exec(Map.of("command", "git rm --dry-run file.txt"));
        assertFalse(result.content().contains("Blocked"));
    }

    // --- Shell injection attempts ---

    @Test
    void shellInjectionWithSemicolonAndCatastrophicIsBlocked() {
        ToolResult result = exec(Map.of("command", "echo safe; rm -rf /"));
        assertTrue(result.isError(), "Command should be blocked by catastrophic check");
        assertTrue(result.content().contains("Blocked"));
    }

    @Test
    void shellInjectionWithBackticksIsContained() {
        ToolResult result = exec(Map.of("command", "echo `whoami`"));
        assertFalse(result.isError());
    }

    @Test
    void shellInjectionWithDollarParenthesisIsContained() {
        ToolResult result = exec(Map.of("command", "echo $(date)"));
        assertFalse(result.isError());
    }

    // --- Newline injection ---

    @Test
    void commandWithNewlineRunsBothLines() {
        ToolResult result = exec(Map.of("command", "echo first\necho second"));
        assertFalse(result.isError());
        assertTrue(result.content().contains("first"));
        assertTrue(result.content().contains("second"));
    }

    // --- Timeout enforcement ---

    @Test
    void timeoutForcesTerminationAndReturnsError() {
        ToolResult result = exec(Map.of("command", "sleep 10", "timeout", 1));
        assertTrue(result.isError(), "Timed-out command should return isError=true");
        assertTrue(result.content().contains("timed out"));
        assertEquals(-1, result.metadata().get("exitCode"));
        assertEquals(true, result.metadata().get("timedOut"));
    }

    @Test
    void timeoutLeavesNoZombieProcess() {
        for (int i = 0; i < 3; i++) {
            ToolResult result = exec(Map.of("command", "sleep 60", "timeout", 1));
            assertTrue(result.isError());
        }
    }

    // --- Sandbox switching consistency ---

    @Test
    void mockDockerSandboxProducesSameResultShape() {
        SandboxHandle mockHandle = createSuccessfulHandle("docker-output", 0);
        when(mockSandbox.start(any(SandboxRequest.class))).thenReturn(mockHandle);

        ToolResult result = exec(Map.of("command", "echo test"), mockContext);
        assertFalse(result.isError());
        assertTrue(result.content().contains("docker-output"));
        assertEquals(0, result.metadata().get("exitCode"));
    }

    @Test
    void mockSandboxTimeoutProducesConsistentError() {
        SandboxHandle mockHandle = createTimedOutHandle();
        when(mockSandbox.start(any(SandboxRequest.class))).thenReturn(mockHandle);

        ToolResult result = exec(Map.of("command", "slow-cmd"), mockContext);
        assertTrue(result.isError());
        assertTrue(result.content().contains("timed out"));
        assertEquals(true, result.metadata().get("timedOut"));
    }

    // --- Empty and whitespace commands ---

    @Test
    void emptyCommandReturnsError() {
        ToolResult result = exec(Map.of("command", ""));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'command' is required"));
    }

    @Test
    void whitespaceOnlyCommandReturnsError() {
        ToolResult result = exec(Map.of("command", "   \t\n  "));
        assertTrue(result.isError());
    }

    // --- Output truncation ---

    @Test
    void largeOutputIsTruncatedAtThreshold() {
        ToolResult result =
                exec(Map.of("command", "for i in $(seq 1 2000); do printf '%0100d' 0; done"));
        assertFalse(result.isError());
        assertTrue(
                result.content().contains("output truncated"),
                "Output should contain truncation notice");
        assertTrue(result.content().length() < 120_000, "Truncated output should be bounded");
    }

    // --- Invalid working directory ---

    @Test
    void nonexistentWorkingDirectoryReturnsError() {
        ToolResult result =
                exec(
                        Map.of(
                                "command", "echo test",
                                "workingDirectory", "/this/path/does/not/exist/abc123"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("Working directory does not exist"));
    }

    // --- Exit code mapping ---

    @Test
    void exitCodeZeroIsNotError() {
        ToolResult result = exec(Map.of("command", "exit 0"));
        assertFalse(result.isError());
        assertEquals(0, result.metadata().get("exitCode"));
    }

    @Test
    void exitCodeOneIsError() {
        ToolResult result = exec(Map.of("command", "exit 1"));
        assertTrue(result.isError());
        assertEquals(1, result.metadata().get("exitCode"));
    }

    @Test
    void exitCodeTwoIsMappedCorrectly() {
        ToolResult result = exec(Map.of("command", "exit 2"));
        assertTrue(result.isError());
        assertEquals(2, result.metadata().get("exitCode"));
    }

    @Test
    void exitCode127ForCommandNotFound() {
        ToolResult result = exec(Map.of("command", "nonexistent_command_xyz"));
        assertTrue(result.isError());
        assertEquals(127, result.metadata().get("exitCode"));
    }

    // --- IDLE_TIMEOUT diagnostic ---

    @Test
    void idleTimeoutSignalProducesDiagnosticMessage() {
        SandboxHandle mockHandle = createIdleTimeoutHandle("partial-output");
        when(mockSandbox.start(any(SandboxRequest.class))).thenReturn(mockHandle);

        ToolResult result = exec(Map.of("command", "cat"), mockContext);
        assertTrue(result.isError(), "IDLE_TIMEOUT should be an error");
        assertEquals(ToolOutcome.ERROR, result.outcome());
        assertTrue(
                result.content().contains("no stdout for"),
                "Should contain 'no stdout for' diagnostic: " + result.content());
        assertTrue(
                result.content().contains("KAIRO_BASH_IDLE_TIMEOUT_S"),
                "Should mention KAIRO_BASH_IDLE_TIMEOUT_S env var: " + result.content());
        assertEquals("IDLE_TIMEOUT", result.metadata().get("signal"));
        assertEquals(-1, result.metadata().get("exitCode"));
    }

    @Test
    void idleTimeoutWithNoOutputShowsDiagnosticOnly() {
        SandboxHandle mockHandle = createIdleTimeoutHandle("");
        when(mockSandbox.start(any(SandboxRequest.class))).thenReturn(mockHandle);

        ToolResult result = exec(Map.of("command", "stalled-cmd"), mockContext);
        assertTrue(result.isError());
        assertTrue(result.content().contains("no stdout for"));
        assertTrue(result.content().contains("KAIRO_BASH_IDLE_TIMEOUT_S"));
    }

    // --- Helper factories for mock handles ---

    private SandboxHandle createSuccessfulHandle(String output, int exitCode) {
        return new SandboxHandle() {
            @Override
            public Flux<SandboxOutputChunk> output() {
                return Flux.just(
                        new SandboxOutputChunk.Stdout(output.getBytes(StandardCharsets.UTF_8)));
            }

            @Override
            public Mono<SandboxExit> exit() {
                return Mono.just(new SandboxExit(exitCode, null, false, false));
            }

            @Override
            public void cancel() {}

            @Override
            public void close() {}
        };
    }

    private SandboxHandle createTimedOutHandle() {
        return new SandboxHandle() {
            @Override
            public Flux<SandboxOutputChunk> output() {
                return Flux.empty();
            }

            @Override
            public Mono<SandboxExit> exit() {
                return Mono.just(new SandboxExit(-1, "TIMEOUT", true, false));
            }

            @Override
            public void cancel() {}

            @Override
            public void close() {}
        };
    }

    private SandboxHandle createIdleTimeoutHandle(String output) {
        return new SandboxHandle() {
            @Override
            public Flux<SandboxOutputChunk> output() {
                if (output.isEmpty()) {
                    return Flux.empty();
                }
                return Flux.just(
                        new SandboxOutputChunk.Stdout(output.getBytes(StandardCharsets.UTF_8)));
            }

            @Override
            public Mono<SandboxExit> exit() {
                return Mono.just(new SandboxExit(-1, "IDLE_TIMEOUT", false, false));
            }

            @Override
            public void cancel() {}

            @Override
            public void close() {}
        };
    }
}
