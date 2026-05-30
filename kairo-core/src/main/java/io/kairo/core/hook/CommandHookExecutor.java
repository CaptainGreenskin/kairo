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
package io.kairo.core.hook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.hook.ExternalHookConfig;
import io.kairo.api.hook.ExternalHookExecutor;
import io.kairo.api.hook.HookEvent;
import io.kairo.api.hook.HookResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Executes external hooks as subprocess commands, following the Claude Code JSON stdin/stdout
 * protocol.
 *
 * <p>Protocol:
 *
 * <ul>
 *   <li>Serializes the {@link HookEvent} as JSON to the subprocess stdin
 *   <li>Reads stdout as JSON response with optional {@code decision}, {@code reason}, {@code
 *       modifiedInput} fields
 *   <li>Exit code 0 = CONTINUE (or whatever stdout specifies)
 *   <li>Exit code 2 = ABORT (block the operation)
 *   <li>Other exit codes = CONTINUE with warning logged
 * </ul>
 */
public class CommandHookExecutor implements ExternalHookExecutor {

    private static final Logger log = LoggerFactory.getLogger(CommandHookExecutor.class);

    private static final int EXIT_CONTINUE = 0;
    private static final int EXIT_BLOCK = 2;

    private final ObjectMapper objectMapper;

    public CommandHookExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return "command";
    }

    @Override
    public <T extends HookEvent> Mono<HookResult<T>> execute(T event, ExternalHookConfig config) {
        if (config.command() == null || config.command().isBlank()) {
            return Mono.just(HookResult.proceed(event));
        }

        // Errors are intentionally NOT swallowed here: see HttpHookExecutor.execute for the
        // rationale — DefaultHookChain.fireExternalHooks catches and feeds the failure to
        // recordExternalHookFailure so the observability pipeline sees it before degrading to
        // CONTINUE.
        return Mono.fromCallable(() -> executeBlocking(event, config))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @SuppressWarnings("unchecked")
    private <T extends HookEvent> HookResult<T> executeBlocking(T event, ExternalHookConfig config)
            throws Exception {
        String[] cmd = buildCommand(config.command());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);

        Process process = pb.start();
        // Drain stdout/stderr on dedicated threads started BEFORE waitFor. Reading the pipes
        // after waitFor() returns races with the JVM's process reaper closing/swapping the
        // ProcessPipeInputStream, which surfaced as intermittent "Stream closed" on CI (the
        // exit-2 hook then fell back to CONTINUE instead of ABORT). Continuous draining from
        // start also prevents a full pipe buffer from deadlocking a chatty hook.
        AtomicReference<byte[]> stdoutRef = new AtomicReference<>(new byte[0]);
        AtomicReference<byte[]> stderrRef = new AtomicReference<>(new byte[0]);
        Thread outDrain = drain(process.getInputStream(), stdoutRef);
        Thread errDrain = drain(process.getErrorStream(), stderrRef);
        try {
            byte[] inputJson = objectMapper.writeValueAsBytes(event);
            try (OutputStream os = process.getOutputStream()) {
                os.write(inputJson);
                os.flush();
            } catch (IOException pipeClosed) {
                // A hook command that doesn't consume stdin (or exits before we finish writing —
                // e.g. `echo ...; exit 2`) closes the pipe early, so the write/close throws
                // "Stream closed" / broken pipe. The exit code + stdout still define the result,
                // so this must not fail the hook. (This was the real cause of the flaky
                // CommandHookExecutor failures on CI.)
                log.debug(
                        "Command hook did not consume stdin [{}]: {}",
                        config.command(),
                        pipeClosed.getMessage());
            }

            boolean finished = process.waitFor(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn(
                        "Command hook timed out after {}ms: {}",
                        config.timeout().toMillis(),
                        config.command());
                return HookResult.proceed(event);
            }

            int exitCode = process.exitValue();
            // Process has exited; the drain threads see EOF and finish. Bound the join so a
            // stuck reader can never hang the hook.
            outDrain.join(2000);
            errDrain.join(2000);
            String stdout = new String(stdoutRef.get(), StandardCharsets.UTF_8);
            String stderr = new String(stderrRef.get(), StandardCharsets.UTF_8);

            if (!stderr.isBlank()) {
                log.debug("Command hook stderr [{}]: {}", config.command(), stderr.trim());
            }

            if (exitCode == EXIT_BLOCK) {
                String reason = parseReason(stdout, "Blocked by command hook: " + config.command());
                return HookResult.abort(event, reason);
            }

            if (exitCode != EXIT_CONTINUE) {
                log.warn(
                        "Command hook exited with code {} [{}]: {}",
                        exitCode,
                        config.command(),
                        stderr.isBlank() ? stdout : stderr);
                return HookResult.proceed(event);
            }

            if (stdout.isBlank()) {
                return HookResult.proceed(event);
            }

            return parseResponse(event, stdout, config.command());
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends HookEvent> HookResult<T> parseResponse(
            T event, String stdout, String command) {
        try {
            JsonNode root = objectMapper.readTree(stdout);

            String decisionStr = root.has("decision") ? root.get("decision").asText() : "CONTINUE";
            HookResult.Decision decision;
            try {
                decision = HookResult.Decision.valueOf(decisionStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown decision '{}' from command hook [{}]", decisionStr, command);
                decision = HookResult.Decision.CONTINUE;
            }

            String reason = root.has("reason") ? root.get("reason").asText() : null;

            Map<String, Object> modifiedInput = null;
            if (root.has("modifiedInput")) {
                modifiedInput = objectMapper.convertValue(root.get("modifiedInput"), Map.class);
            }

            String injectedContext =
                    root.has("injectedContext") ? root.get("injectedContext").asText() : null;

            return new HookResult<>(
                    event, decision, injectedContext, modifiedInput, reason, null, command);
        } catch (Exception e) {
            log.warn("Failed to parse command hook output [{}]: {}", command, e.getMessage());
            return HookResult.proceed(event);
        }
    }

    private String parseReason(String stdout, String fallback) {
        if (stdout.isBlank()) return fallback;
        try {
            JsonNode root = objectMapper.readTree(stdout);
            if (root.has("reason")) {
                return root.get("reason").asText();
            }
        } catch (Exception ignored) {
            // not valid JSON
        }
        return fallback;
    }

    private static String[] buildCommand(String command) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return new String[] {"cmd", "/c", command};
        }
        return new String[] {"/bin/sh", "-c", command};
    }

    /**
     * Reads {@code is} to EOF on a daemon thread, storing the bytes into {@code sink}. Reading on a
     * separate thread started before {@code waitFor} avoids the read-after-reap race that closed
     * the pipe out from under a direct {@code readAllBytes()} call.
     */
    private static Thread drain(InputStream is, AtomicReference<byte[]> sink) {
        Thread t =
                new Thread(
                        () -> {
                            try (is) {
                                sink.set(is.readAllBytes());
                            } catch (IOException ignored) {
                                // stream closed by process teardown / kill — leave sink empty
                            }
                        },
                        "kairo-cmdhook-drain");
        t.setDaemon(true);
        t.start();
        return t;
    }
}
