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
package io.kairo.tools.exec.tck;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.sandbox.ExecutionSandbox;
import io.kairo.api.sandbox.SandboxExit;
import io.kairo.api.sandbox.SandboxHandle;
import io.kairo.api.sandbox.SandboxOutputChunk;
import io.kairo.api.sandbox.SandboxRequest;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Contract tests every {@link ExecutionSandbox} backend must satisfy.
 *
 * <p>Adapter authors extend this class and implement {@link #newSandbox()} to plug their backend
 * in; the suite covers the eight invariants captured in the v1.1 SPI scoping doc:
 *
 * <ol>
 *   <li>successful command emits stdout and exits 0
 *   <li>non-zero exit code is surfaced unchanged
 *   <li>stderr is folded into the output stream (so consumers don't have to subscribe to two
 *       streams)
 *   <li>{@link SandboxRequest#workspaceRoot()} is honoured as the process working directory
 *   <li>{@link SandboxRequest#env()} entries reach the child process
 *   <li>elapsed {@link SandboxRequest#timeout()} kills the process and surfaces {@link
 *       SandboxExit#timedOut()}
 *   <li>output exceeding {@link SandboxRequest#maxOutputBytes()} flips {@link
 *       SandboxExit#truncated()}
 *   <li>{@link SandboxHandle#cancel()} terminates the process and {@link SandboxHandle#close()} is
 *       idempotent
 * </ol>
 *
 * @since v1.1
 */
public abstract class ExecutionSandboxTCK {

    /** Factory hook: each scenario starts with a fresh sandbox instance. */
    protected abstract ExecutionSandbox newSandbox();

    @TempDir Path workspaceRoot;

    private SandboxRequest.Builder request(String command) {
        return new SandboxRequest.Builder(command, workspaceRoot);
    }

    // ------------------------------------------------------------------ scenario 1
    @Test
    void successfulCommand_emitsStdout_andExitsZero() {
        ExecutionSandbox sandbox = newSandbox();
        SandboxRequest req = request("echo hello-tck").build();

        try (SandboxHandle handle = sandbox.start(req)) {
            String out = drainStdout(handle);
            SandboxExit exit = handle.exit().block(Duration.ofSeconds(5));
            assertThat(exit).isNotNull();
            assertThat(exit.exitCode()).isZero();
            assertThat(exit.timedOut()).isFalse();
            assertThat(exit.truncated()).isFalse();
            assertThat(out).contains("hello-tck");
        }
    }

    // ------------------------------------------------------------------ scenario 2
    @Test
    void nonZeroExitCode_isSurfaced() {
        ExecutionSandbox sandbox = newSandbox();
        try (SandboxHandle handle = sandbox.start(request("exit 7").build())) {
            drainStdout(handle);
            SandboxExit exit = handle.exit().block(Duration.ofSeconds(5));
            assertThat(exit).isNotNull();
            assertThat(exit.exitCode()).isEqualTo(7);
            assertThat(exit.timedOut()).isFalse();
        }
    }

    // ------------------------------------------------------------------ scenario 3
    @Test
    void stderr_isFolded_intoOutputStream() {
        ExecutionSandbox sandbox = newSandbox();
        try (SandboxHandle handle =
                sandbox.start(request("echo to-stderr 1>&2; echo to-stdout").build())) {
            String combined = drainStdout(handle);
            handle.exit().block(Duration.ofSeconds(5));
            assertThat(combined).contains("to-stderr").contains("to-stdout");
        }
    }

    // ------------------------------------------------------------------ scenario 4
    @Test
    void workspaceRoot_isUsedAs_workingDirectory() throws Exception {
        Path subdir = Files.createDirectory(workspaceRoot.resolve("nested"));
        ExecutionSandbox sandbox = newSandbox();
        SandboxRequest req = new SandboxRequest.Builder("pwd", subdir).build();

        try (SandboxHandle handle = sandbox.start(req)) {
            String out = drainStdout(handle);
            handle.exit().block(Duration.ofSeconds(5));
            // Compare canonical paths so e.g. macOS /var → /private/var symlinks don't fail us.
            assertThat(Path.of(out.trim()).toRealPath()).isEqualTo(subdir.toRealPath());
        }
    }

    // ------------------------------------------------------------------ scenario 5
    @Test
    void env_isPassed_toChildProcess() {
        ExecutionSandbox sandbox = newSandbox();
        SandboxRequest req =
                new SandboxRequest.Builder("echo $KAIRO_TCK_VAR", workspaceRoot)
                        .env(Map.of("KAIRO_TCK_VAR", "tck-value"))
                        .build();

        try (SandboxHandle handle = sandbox.start(req)) {
            String out = drainStdout(handle);
            handle.exit().block(Duration.ofSeconds(5));
            assertThat(out).contains("tck-value");
        }
    }

    // ------------------------------------------------------------------ scenario 6
    @Test
    void timeoutElapsed_killsProcess_andSurfacesTimedOut() {
        ExecutionSandbox sandbox = newSandbox();
        SandboxRequest req =
                new SandboxRequest.Builder("sleep 5", workspaceRoot)
                        .timeout(Duration.ofMillis(500))
                        .build();

        try (SandboxHandle handle = sandbox.start(req)) {
            handle.output().blockLast(Duration.ofSeconds(5));
            SandboxExit exit = handle.exit().block(Duration.ofSeconds(5));
            assertThat(exit).isNotNull();
            assertThat(exit.timedOut()).isTrue();
            assertThat(exit.signal()).isEqualTo("TIMEOUT");
        }
    }

    // ------------------------------------------------------------------ scenario 7
    @Test
    void outputBeyondMaxBytes_flipsTruncatedFlag() {
        ExecutionSandbox sandbox = newSandbox();
        // produce 4096 'A's — well over 256 byte budget
        SandboxRequest req =
                new SandboxRequest.Builder("head -c 4096 /dev/zero | tr '\\0' A", workspaceRoot)
                        .maxOutputBytes(256L)
                        .build();

        try (SandboxHandle handle = sandbox.start(req)) {
            String out = drainStdout(handle);
            SandboxExit exit = handle.exit().block(Duration.ofSeconds(5));
            assertThat(exit).isNotNull();
            assertThat(exit.truncated()).isTrue();
            assertThat(out.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(256);
        }
    }

    // ------------------------------------------------------------------ scenario 8
    @Test
    void cancelTerminatesProcess_andCloseIsIdempotent() {
        ExecutionSandbox sandbox = newSandbox();
        SandboxRequest req =
                new SandboxRequest.Builder("sleep 30", workspaceRoot)
                        .timeout(Duration.ofSeconds(60))
                        .build();

        SandboxHandle handle = sandbox.start(req);
        handle.cancel();
        SandboxExit exit = handle.exit().block(Duration.ofSeconds(5));
        assertThat(exit).isNotNull();
        assertThat(exit.timedOut()).isFalse();
        assertThat(exit.signal()).isEqualTo("CANCELLED");

        // Idempotent close.
        handle.close();
        handle.close();
    }

    /** Drains stdout chunks into a UTF-8 string, blocking until the stream completes. */
    protected String drainStdout(SandboxHandle handle) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        handle.output()
                .doOnNext(
                        chunk -> {
                            if (chunk instanceof SandboxOutputChunk.Stdout s) {
                                buffer.writeBytes(s.data());
                            } else if (chunk instanceof SandboxOutputChunk.Stderr e) {
                                buffer.writeBytes(e.data());
                            }
                        })
                .blockLast(Duration.ofSeconds(10));
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
