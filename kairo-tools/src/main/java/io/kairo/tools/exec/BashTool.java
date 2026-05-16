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

import io.kairo.api.sandbox.ExecutionSandbox;
import io.kairo.api.sandbox.SandboxExit;
import io.kairo.api.sandbox.SandboxHandle;
import io.kairo.api.sandbox.SandboxOutputChunk;
import io.kairo.api.sandbox.SandboxRequest;
import io.kairo.api.tenant.TenantContext;
import io.kairo.api.tool.ApprovalGate;
import io.kairo.api.tool.Hint;
import io.kairo.api.tool.StreamingTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolEvent;
import io.kairo.api.tool.ToolOutcome;
import io.kairo.api.tool.ToolOutput;
import io.kairo.api.tool.ToolParam;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.api.workspace.Workspace;
import io.kairo.core.tool.CommandSafetyPolicy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

/**
 * Executes a shell command and streams its output as {@link ToolEvent} chunks.
 *
 * <p>Backed by the {@link ExecutionSandbox} SPI. The active sandbox is resolved via {@link
 * ToolContext#getBean(Class)}; when no implementation is bound, BashTool falls back to {@link
 * LocalProcessSandbox#INSTANCE}, preserving v1.0 behaviour byte-for-byte.
 *
 * <p>Emits {@link ToolEvent.Chunk} events for each output fragment, periodic {@link
 * ToolEvent.Progress} heartbeats every 5 seconds for long-running commands, and a terminal {@link
 * ToolEvent.Final} with the aggregated result including {@link BashErrorEnricher} hints.
 *
 * @since 1.2.0
 */
@Tool(
        name = "bash",
        description =
                "Execute a shell command and return its output. Use for running programs, installing packages, or system operations.",
        category = ToolCategory.EXECUTION,
        sideEffect = ToolSideEffect.SYSTEM_CHANGE)
public class BashTool implements StreamingTool {

    private static final Logger log = LoggerFactory.getLogger(BashTool.class);
    private static final long MAX_OUTPUT_BYTES = 100_000L;
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(5);
    private static final long IDLE_TIMEOUT_SECONDS =
            Long.parseLong(System.getenv().getOrDefault("KAIRO_BASH_IDLE_TIMEOUT_S", "60"));

    private final CommandSafetyPolicy commandPolicy = CommandSafetyPolicy.instance();

    private final int defaultTimeoutMs =
            Integer.parseInt(System.getenv().getOrDefault("KAIRO_TOOL_TIMEOUT_MS", "120000"));

    @ToolParam(description = "The shell command to execute", required = true)
    private String command;

    @ToolParam(description = "Timeout in seconds (default: derived from KAIRO_TOOL_TIMEOUT_MS)")
    private Integer timeout;

    @ToolParam(description = "Working directory for the command")
    private String workingDirectory;

    @Override
    public Flux<ToolEvent> stream(Map<String, Object> args, ToolContext ctx) {
        return Flux.defer(
                () -> {
                    String cmd = (String) args.get("command");
                    if (cmd == null || cmd.isBlank()) {
                        return Flux.just(
                                new ToolEvent.Final(error("Parameter 'command' is required")));
                    }

                    int timeoutSec = parseTimeout(args.get("timeout"));
                    Path workspaceRoot = resolveWorkspaceRoot(args.get("workingDirectory"), ctx);
                    if (workspaceRoot == null) {
                        return Flux.just(
                                new ToolEvent.Final(
                                        error(
                                                "Working directory does not exist: "
                                                        + args.get("workingDirectory"))));
                    }

                    // Defense-in-depth: catastrophic check (also enforced by guardrail layer)
                    Optional<String> catastrophic = commandPolicy.checkCatastrophic(cmd);
                    if (catastrophic.isPresent()) {
                        return Flux.just(new ToolEvent.Final(error(catastrophic.get())));
                    }

                    if (commandPolicy.isDangerous(cmd)) {
                        Optional<ApprovalGate> gate =
                                ctx == null ? Optional.empty() : ctx.getBean(ApprovalGate.class);
                        if (gate.isPresent()) {
                            String reason = commandPolicy.buildDangerReason(cmd);
                            return Flux.concat(
                                    Flux.just(new ToolEvent.NeedsApproval(cmd, reason)),
                                    gate.get()
                                            .await(cmd, reason)
                                            .flatMapMany(
                                                    decision ->
                                                            handleDecision(
                                                                    decision,
                                                                    cmd,
                                                                    timeoutSec,
                                                                    workspaceRoot,
                                                                    ctx)));
                        }
                        // No gate bound = headless mode, proceed without approval
                        log.warn(
                                "Dangerous command detected but no ApprovalGate bound, executing:"
                                        + " {}",
                                cmd);
                    }

                    return executeCommand(cmd, timeoutSec, workspaceRoot, ctx);
                });
    }

    private Flux<ToolEvent> handleDecision(
            ApprovalGate.Decision decision,
            String cmd,
            int timeoutSec,
            Path workspaceRoot,
            ToolContext ctx) {
        if (decision instanceof ApprovalGate.Approved a) {
            String effectiveCmd =
                    a.editedArgs()
                            .map(m -> m.get("command"))
                            .filter(v -> v instanceof String)
                            .map(Object::toString)
                            .orElse(cmd);
            return executeCommand(effectiveCmd, timeoutSec, workspaceRoot, ctx);
        }
        if (decision instanceof ApprovalGate.Rejected r) {
            String msg =
                    r.feedback()
                            .map(f -> "Command rejected by user: " + cmd + " — " + f)
                            .orElse("Command rejected by user: " + cmd);
            return Flux.just(
                    new ToolEvent.Final(
                            new ToolResult(
                                    "bash",
                                    new ToolOutput.Text(msg),
                                    ToolOutcome.CANCELLED,
                                    List.of(),
                                    Map.of("command", cmd))));
        }
        // Unreachable: Decision is sealed permits Approved | Rejected
        throw new IllegalStateException("Unknown ApprovalGate.Decision: " + decision);
    }

    /** Executes the command in a sandbox, streaming output chunks and heartbeats. */
    private Flux<ToolEvent> executeCommand(
            String cmd, int timeoutSec, Path workspaceRoot, ToolContext ctx) {
        ExecutionSandbox sandbox =
                ctx == null
                        ? LocalProcessSandbox.INSTANCE
                        : ctx.getBean(ExecutionSandbox.class).orElse(LocalProcessSandbox.INSTANCE);
        TenantContext tenant = ctx == null ? TenantContext.SINGLE : ctx.tenant();

        SandboxRequest request =
                new SandboxRequest(
                        cmd,
                        workspaceRoot,
                        Map.of(),
                        Duration.ofSeconds(timeoutSec),
                        MAX_OUTPUT_BYTES,
                        tenant,
                        false);

        log.debug("Executing command (streaming): {}", cmd);
        SandboxHandle handle = sandbox.start(request);

        // Accumulator for building the final result
        StringBuilder outputBuffer = new StringBuilder();

        // Stream output chunks
        Flux<ToolEvent> chunks =
                handle.output()
                        .map(
                                chunk -> {
                                    String text;
                                    if (chunk instanceof SandboxOutputChunk.Stdout s) {
                                        text = new String(s.data(), StandardCharsets.UTF_8);
                                    } else if (chunk instanceof SandboxOutputChunk.Stderr e) {
                                        text = new String(e.data(), StandardCharsets.UTF_8);
                                    } else {
                                        text = "";
                                    }
                                    outputBuffer.append(text);
                                    ToolEvent.StreamKind kind =
                                            (chunk instanceof SandboxOutputChunk.Stderr)
                                                    ? ToolEvent.StreamKind.STDERR
                                                    : ToolEvent.StreamKind.STDOUT;
                                    return (ToolEvent) new ToolEvent.Chunk(text, kind);
                                });

        // Periodic heartbeat for long-running commands
        Flux<ToolEvent> heartbeat =
                Flux.interval(HEARTBEAT_INTERVAL)
                        .map(i -> (ToolEvent) new ToolEvent.Progress(-1.0, "Running..."));

        // Merge chunks + heartbeat, take until exit, then emit Final
        return Flux.merge(chunks, heartbeat)
                .takeUntilOther(handle.exit().delayElement(Duration.ofMillis(100)))
                .concatWith(
                        handle.exit()
                                .flatMapMany(
                                        exit ->
                                                buildFinalEvent(
                                                        cmd, outputBuffer.toString(), exit)));
    }

    private Flux<ToolEvent> buildFinalEvent(String cmd, String output, SandboxExit exit) {
        int exitCode = exit.exitCode();
        boolean timedOut = exit.timedOut();
        List<Hint> hints = BashErrorEnricher.enrich(output, exitCode, timedOut);

        ToolResult result;
        if (timedOut) {
            String timeoutOutput =
                    output.isEmpty()
                            ? "Command '" + cmd + "' timed out"
                            : output + "\n\n(Command timed out)";
            result =
                    new ToolResult(
                            "bash",
                            new ToolOutput.Text(timeoutOutput),
                            ToolOutcome.TIMEOUT,
                            hints,
                            Map.of("exitCode", -1, "timedOut", true, "command", cmd));
        } else if ("IDLE_TIMEOUT".equals(exit.signal())) {
            String diagnostic =
                    String.format(
                            "Process killed: no stdout for %ds (likely stalled or waiting for"
                                    + " interactive input).%n"
                                    + "Hint: add --yes/--non-interactive flags, or increase"
                                    + " KAIRO_BASH_IDLE_TIMEOUT_S.",
                            IDLE_TIMEOUT_SECONDS);
            String idleOutput = output.isEmpty() ? diagnostic : output + "\n\n" + diagnostic;
            result =
                    new ToolResult(
                            "bash",
                            new ToolOutput.Text(idleOutput),
                            ToolOutcome.ERROR,
                            hints,
                            Map.of("exitCode", -1, "signal", "IDLE_TIMEOUT", "command", cmd));
        } else if (exitCode != 0) {
            result =
                    new ToolResult(
                            "bash",
                            new ToolOutput.Text(output),
                            ToolOutcome.ERROR,
                            hints,
                            Map.of("exitCode", exitCode, "command", cmd));
        } else {
            result =
                    ToolResult.success(
                            "bash", output, Map.of("exitCode", exitCode, "command", cmd));
        }

        if (exit.truncated()) {
            String truncatedOutput =
                    output + "\n... (output truncated at " + MAX_OUTPUT_BYTES + " bytes)";
            result =
                    new ToolResult(
                            result.toolUseId(),
                            new ToolOutput.Text(truncatedOutput),
                            result.outcome(),
                            result.hints(),
                            result.metadata());
        }

        return Flux.just(new ToolEvent.Final(result));
    }

    private int parseTimeout(Object timeoutObj) {
        if (timeoutObj instanceof Number n) {
            return n.intValue();
        }
        if (timeoutObj instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return defaultTimeoutMs / 1000;
    }

    /**
     * Resolves the working directory: explicit {@code workingDirectory} param wins; otherwise uses
     * the active workspace (or JVM cwd when no context). Returns {@code null} if an explicit
     * directory was supplied but does not exist.
     */
    private static Path resolveWorkspaceRoot(Object explicitDir, ToolContext context) {
        if (explicitDir instanceof String s && !s.isBlank()) {
            Path explicit = Path.of(s);
            return Files.isDirectory(explicit) ? explicit : null;
        }
        return context == null ? Workspace.cwd().root() : context.workspace().root();
    }

    private ToolResult error(String msg) {
        return ToolResult.error("bash", msg);
    }
}
