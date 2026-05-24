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

import io.kairo.api.sandbox.SandboxExit;
import io.kairo.api.sandbox.SandboxHandle;
import io.kairo.api.sandbox.SandboxOutputChunk;
import io.kairo.api.sandbox.SandboxRequest;
import io.kairo.api.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for the idle watchdog in {@link LocalProcessSandbox}.
 *
 * <p>Uses a short (3 s) idle timeout via the package-private constructor to keep tests fast.
 */
class LocalProcessSandboxIdleTimeoutTest {

    private static final Duration SHORT_IDLE_TIMEOUT = Duration.ofSeconds(3);
    private static final Path WORKSPACE = Path.of(System.getProperty("user.dir"));

    private final LocalProcessSandbox sandbox = new LocalProcessSandbox(SHORT_IDLE_TIMEOUT);

    private SandboxRequest request(String command) {
        return new SandboxRequest(
                command,
                WORKSPACE,
                Map.of(),
                Duration.ofSeconds(30),
                100_000L,
                TenantContext.SINGLE,
                false);
    }

    @Test
    void processWithRegularOutputCompletesNormally() {
        // Produces output every second — well within the 3s idle timeout
        SandboxHandle handle =
                sandbox.start(request("for i in 1 2 3 4 5; do echo $i; sleep 1; done"));

        SandboxExit exit = handle.exit().block(Duration.ofSeconds(15));

        assertNotNull(exit);
        assertEquals(0, exit.exitCode(), "Process should exit cleanly");
        assertNotEquals("IDLE_TIMEOUT", exit.signal(), "Should NOT be killed by idle watchdog");
        assertFalse(exit.timedOut(), "Should not be a timeout");

        // Verify we got all the output
        String output =
                handle
                        .output()
                        .filter(c -> c instanceof SandboxOutputChunk.Stdout)
                        .map(
                                c ->
                                        new String(
                                                ((SandboxOutputChunk.Stdout) c).data(),
                                                StandardCharsets.UTF_8))
                        .collectList()
                        .block(Duration.ofSeconds(1))
                        .stream()
                        .reduce("", String::concat);
        assertTrue(output.contains("5"), "Should have received all output including '5'");
    }

    @Test
    void hangingProcessIsIdleKilled() {
        // 'sleep 999' produces no output — should trigger idle timeout after ~3s
        SandboxHandle handle = sandbox.start(request("sleep 999"));

        long startNanos = System.nanoTime();
        SandboxExit exit =
                handle.exit().block(Duration.ofSeconds(SHORT_IDLE_TIMEOUT.toSeconds() + 10));
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        assertNotNull(exit);
        assertEquals("IDLE_TIMEOUT", exit.signal(), "Should be killed by idle watchdog");
        assertNotEquals(0, exit.exitCode(), "Exit code should be non-zero after forced kill");

        // Should complete within idle timeout + reasonable tolerance (5s)
        assertTrue(
                elapsedMs < (SHORT_IDLE_TIMEOUT.toMillis() + 5_000),
                "Should be killed within timeout + 5s tolerance, took: " + elapsedMs + "ms");
    }

    @Test
    void idleKilledProcessHasNonZeroExitCode() {
        // 'cat' with no stdin will hang forever — triggers idle timeout
        SandboxHandle handle = sandbox.start(request("cat /dev/stdin"));

        SandboxExit exit =
                handle.exit().block(Duration.ofSeconds(SHORT_IDLE_TIMEOUT.toSeconds() + 10));

        assertNotNull(exit);
        assertEquals("IDLE_TIMEOUT", exit.signal());
        assertEquals(-1, exit.exitCode(), "Exit code should be -1 for idle-timeout kill");
        assertFalse(
                exit.timedOut(),
                "timedOut flag should be false (this is idle timeout, not request timeout)");
    }
}
