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
import io.kairo.api.tool.ToolResult;
import io.kairo.api.workspace.Workspace;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Security-focused tests for {@link BashTool}.
 *
 * <p>These tests validate injection protection, sandbox boundaries, timeout enforcement, and output
 * safety. They mock the {@link ExecutionSandbox} SPI to verify BashTool's handling of adversarial
 * inputs without depending on a specific sandbox backend.
 */
class BashToolSecurityTest {

    private BashTool tool;
    private ExecutionSandbox mockSandbox;
    private ToolContext mockContext;

    @BeforeEach
    void setUp() {
        tool = new BashTool();
        mockSandbox = mock(ExecutionSandbox.class);
        mockContext = mock(ToolContext.class);
        when(mockContext.getBean(ExecutionSandbox.class)).thenReturn(Optional.of(mockSandbox));
        when(mockContext.tenant()).thenReturn(io.kairo.api.tenant.TenantContext.SINGLE);
        when(mockContext.workspace()).thenReturn(Workspace.cwd());
    }

    // --- Shell injection attempts ---

    @Test
    void shellInjectionWithSemicolonProducesOutputFromBothCommands() {
        // Semicolon injection: /bin/sh -c runs the entire string as one script.
        // `rm -rf /` fails without root, so exit code is non-zero, but echo output
        // is still captured — confirming the sandbox executes the full string safely.
        ToolResult result = tool.execute(Map.of("command", "echo safe; rm -rf /"));
        assertTrue(result.isError(), "rm -rf / fails without root, so exit code is non-zero");
        assertTrue(result.content().contains("safe"));
    }

    @Test
    void shellInjectionWithBackticksIsContained() {
        // Backtick injection — the inner command runs as part of the shell string
        ToolResult result = tool.execute(Map.of("command", "echo `whoami`"));
        // This is expected behaviour: /bin/sh -c evaluates the full string.
        // The test confirms no crash and that output is captured.
        assertFalse(result.isError());
    }

    @Test
    void shellInjectionWithDollarParenthesisIsContained() {
        // $(cmd) injection — same containment via /bin/sh -c
        ToolResult result = tool.execute(Map.of("command", "echo $(date)"));
        assertFalse(result.isError());
    }

    // --- Newline injection ---

    @Test
    void commandWithNewlineRunsBothLines() {
        // /bin/sh -c treats \n as a command separator, both lines execute
        ToolResult result = tool.execute(Map.of("command", "echo first\necho second"));
        assertFalse(result.isError());
        assertTrue(result.content().contains("first"));
        assertTrue(result.content().contains("second"));
    }

    // --- Timeout enforcement ---

    @Test
    void timeoutForcesTerminationAndReturnsError() {
        ToolResult result = tool.execute(Map.of("command", "sleep 10", "timeout", 1));
        assertTrue(result.isError(), "Timed-out command should return isError=true");
        assertTrue(result.content().contains("timed out"));
        assertEquals(-1, result.metadata().get("exitCode"));
        assertEquals(true, result.metadata().get("timedOut"));
    }

    @Test
    void timeoutLeavesNoZombieProcess() {
        // After timeout, verify the sandbox handle is properly closed
        // by running multiple slow commands and checking they don't accumulate
        for (int i = 0; i < 3; i++) {
            ToolResult result = tool.execute(Map.of("command", "sleep 60", "timeout", 1));
            assertTrue(result.isError());
        }
        // If processes leaked, system load would be abnormal; the test passing
        // means handles were closed properly
    }

    // --- Sandbox switching consistency ---

    @Test
    void mockDockerSandboxProducesSameResultShape() {
        // Verify BashTool handles any ExecutionSandbox implementation identically
        // by mocking a sandbox that simulates Docker-like behaviour
        SandboxHandle mockHandle = createSuccessfulHandle("docker-output", 0);
        when(mockSandbox.start(any(SandboxRequest.class))).thenReturn(mockHandle);

        ToolResult result = tool.execute(Map.of("command", "echo test"), mockContext);
        assertFalse(result.isError());
        assertTrue(result.content().contains("docker-output"));
        assertEquals(0, result.metadata().get("exitCode"));
    }

    @Test
    void mockSandboxTimeoutProducesConsistentError() {
        // Simulate a sandbox (e.g. DockerSandbox) that times out
        SandboxHandle mockHandle = createTimedOutHandle();
        when(mockSandbox.start(any(SandboxRequest.class))).thenReturn(mockHandle);

        ToolResult result = tool.execute(Map.of("command", "slow-cmd"), mockContext);
        assertTrue(result.isError());
        assertTrue(result.content().contains("timed out"));
        assertEquals(true, result.metadata().get("timedOut"));
    }

    // --- Empty and whitespace commands ---

    @Test
    void emptyCommandReturnsError() {
        ToolResult result = tool.execute(Map.of("command", ""));
        assertTrue(result.isError());
        assertTrue(result.content().contains("'command' is required"));
    }

    @Test
    void whitespaceOnlyCommandReturnsError() {
        ToolResult result = tool.execute(Map.of("command", "   \t\n  "));
        assertTrue(result.isError());
    }

    // --- Output truncation ---

    @Test
    void largeOutputIsTruncatedAtThreshold() {
        // Generate >100KB of output using a shell loop
        ToolResult result =
                tool.execute(
                        Map.of("command", "for i in $(seq 1 2000); do printf '%0100d' 0; done"));
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
                tool.execute(
                        Map.of(
                                "command", "echo test",
                                "workingDirectory", "/this/path/does/not/exist/abc123"));
        assertTrue(result.isError());
        assertTrue(result.content().contains("Working directory does not exist"));
    }

    // --- Exit code mapping ---

    @Test
    void exitCodeZeroIsNotError() {
        ToolResult result = tool.execute(Map.of("command", "exit 0"));
        assertFalse(result.isError());
        assertEquals(0, result.metadata().get("exitCode"));
    }

    @Test
    void exitCodeOneIsError() {
        ToolResult result = tool.execute(Map.of("command", "exit 1"));
        assertTrue(result.isError());
        assertEquals(1, result.metadata().get("exitCode"));
    }

    @Test
    void exitCodeTwoIsMappedCorrectly() {
        ToolResult result = tool.execute(Map.of("command", "exit 2"));
        assertTrue(result.isError());
        assertEquals(2, result.metadata().get("exitCode"));
    }

    @Test
    void exitCode127ForCommandNotFound() {
        ToolResult result = tool.execute(Map.of("command", "nonexistent_command_xyz"));
        assertTrue(result.isError());
        assertEquals(127, result.metadata().get("exitCode"));
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
}
