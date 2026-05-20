/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.hook;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.plugin.PluginComponent.HookComponent.HookAction;
import io.kairo.plugin.variable.PluginVariableResolver;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Executes one {@link HookAction} produced by the {@link HookComponentLoader}.
 *
 * <p>Built-in handlers:
 *
 * <ul>
 *   <li>{@code command} — fork a child process, send the event payload as JSON on stdin, read
 *       stdout. Honours {@code shell}, {@code args}, {@code env}, {@code timeout}.
 *   <li>{@code http} — POST the event JSON to the configured URL; honours {@code headers} and
 *       {@code timeout}.
 * </ul>
 *
 * <p>Other action types ({@code prompt}, {@code agent}, {@code mcp_tool}, or any custom string) are
 * dispatched through {@link HookActionHandler}s registered via {@link #withHandler}. The host
 * application supplies these — kairo-plugin doesn't bind to a model provider or MCP runtime
 * directly; that coupling lives at the assembly layer ({@code AssistantAgentFactory}).
 *
 * <p>The returned {@link HookResult#decision()} carries the JSON object the action emitted on
 * stdout (or http body) — callers can read fields like {@code continue}, {@code stopReason}, {@code
 * modify}, etc., the same way Claude Code does. If the action emits non-JSON output, the raw text
 * is preserved in {@link HookResult#rawOutput()} and {@code decision} is empty.
 *
 * <p>Timeouts default to the Claude-Code-documented numbers: 600s for command/http/mcp_tool, 30s
 * for prompt, 60s for agent.
 */
public final class HookExecutor {

    private static final Logger log = LoggerFactory.getLogger(HookExecutor.class);

    private static final long DEFAULT_TIMEOUT_COMMAND = 600;
    private static final long DEFAULT_TIMEOUT_HTTP = 600;
    // Defaults for prompt/agent/mcp_tool will be added in Phase B.7 when those types are wired:
    //   prompt = 30s, agent = 60s, mcp_tool = 600s (per Claude Code defaults).

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient httpClient =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final java.util.concurrent.ConcurrentHashMap<String, HookActionHandler> extraHandlers =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Registers a {@link HookActionHandler} for a non-builtin action type ({@code prompt}, {@code
     * agent}, {@code mcp_tool}, or any custom string). Returns this executor for chaining.
     *
     * <p>Handlers for {@code command} or {@code http} are ignored — those types are always handled
     * by the built-in implementations. Re-registering the same {@code handler.type()} replaces the
     * previous handler.
     */
    public HookExecutor withHandler(HookActionHandler handler) {
        Objects.requireNonNull(handler, "handler");
        if ("command".equals(handler.type()) || "http".equals(handler.type())) {
            log.warn(
                    "Refusing to register custom handler for built-in action type '{}'",
                    handler.type());
            return this;
        }
        extraHandlers.put(handler.type(), handler);
        return this;
    }

    /** Whether a handler for the given action type is registered (or is built-in). */
    public boolean hasHandler(String type) {
        return "command".equals(type) || "http".equals(type) || extraHandlers.containsKey(type);
    }

    /**
     * Executes one action.
     *
     * @param action what to run
     * @param eventPayload key-value bag describing the event (sent as stdin JSON / HTTP body)
     * @param resolver variable resolver for {@code ${KAIRO_PLUGIN_ROOT}} etc.; may be null
     */
    public Mono<HookResult> execute(
            HookAction action, Map<String, Object> eventPayload, PluginVariableResolver resolver) {
        Objects.requireNonNull(action, "action");
        Map<String, Object> payload = eventPayload == null ? Map.of() : eventPayload;

        // Built-in types run synchronously on boundedElastic so the IO blocks are isolated.
        if ("command".equals(action.type()) || "http".equals(action.type())) {
            return Mono.fromCallable(() -> dispatchBuiltin(action, payload, resolver))
                    .subscribeOn(Schedulers.boundedElastic());
        }
        // Custom handlers manage their own scheduling.
        HookActionHandler handler = extraHandlers.get(action.type());
        if (handler != null) {
            return handler.execute(action, payload, resolver);
        }
        log.warn(
                "No handler registered for hook action type '{}'; treating as no-op",
                action.type());
        return Mono.just(HookResult.empty());
    }

    private HookResult dispatchBuiltin(
            HookAction action, Map<String, Object> payload, PluginVariableResolver resolver)
            throws IOException, InterruptedException {
        return switch (action.type()) {
            case "command" -> runCommand(action, payload, resolver);
            case "http" -> runHttp(action, payload, resolver);
            default ->
                    throw new IllegalStateException(
                            "dispatchBuiltin called with non-builtin type: " + action.type());
        };
    }

    // ── command ────────────────────────────────────────────────────────────

    private HookResult runCommand(
            HookAction action, Map<String, Object> payload, PluginVariableResolver resolver)
            throws IOException, InterruptedException {

        String command = resolveString(action.config(), "command", resolver);
        if (command == null || command.isBlank()) {
            return HookResult.error("command action missing 'command' field");
        }

        List<String> argv = new ArrayList<>();
        String shell = stringOrNull(action.config(), "shell");
        if (shell != null && !shell.isBlank()) {
            argv.add(shell);
            argv.add("-c");
            StringBuilder shellLine = new StringBuilder(command);
            List<?> args = listOrEmpty(action.config(), "args");
            for (Object a : args) {
                shellLine.append(' ').append(resolve(String.valueOf(a), resolver));
            }
            argv.add(shellLine.toString());
        } else {
            argv.add(command);
            for (Object a : listOrEmpty(action.config(), "args")) {
                argv.add(resolve(String.valueOf(a), resolver));
            }
        }

        long timeoutSec = numberOrDefault(action.config(), "timeout", DEFAULT_TIMEOUT_COMMAND);

        ProcessBuilder pb = new ProcessBuilder(argv);
        pb.redirectErrorStream(false);
        Map<String, String> env = stringMapOrEmpty(action.config(), "env");
        if (!env.isEmpty()) {
            for (Map.Entry<String, String> entry : env.entrySet()) {
                pb.environment().put(entry.getKey(), resolve(entry.getValue(), resolver));
            }
        }

        Process proc = pb.start();
        try (OutputStream stdin = proc.getOutputStream()) {
            stdin.write(json.writeValueAsBytes(payload));
        } catch (IOException ignored) {
            // process may close stdin early; not fatal
        }

        boolean finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            return HookResult.error("command timed out after " + timeoutSec + "s");
        }

        String stdout = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(proc.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        return new HookResult(proc.exitValue(), stdout, stderr, parseDecisionJson(stdout));
    }

    // ── http ──────────────────────────────────────────────────────────────

    private HookResult runHttp(
            HookAction action, Map<String, Object> payload, PluginVariableResolver resolver)
            throws IOException, InterruptedException {

        String url = resolveString(action.config(), "url", resolver);
        if (url == null || url.isBlank()) {
            return HookResult.error("http action missing 'url' field");
        }
        long timeoutSec = numberOrDefault(action.config(), "timeout", DEFAULT_TIMEOUT_HTTP);

        HttpRequest.Builder req =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(timeoutSec))
                        .header("Content-Type", "application/json")
                        .POST(BodyPublishers.ofByteArray(json.writeValueAsBytes(payload)));
        Map<String, String> headers = stringMapOrEmpty(action.config(), "headers");
        for (Map.Entry<String, String> h : headers.entrySet()) {
            req.header(h.getKey(), resolve(h.getValue(), resolver));
        }

        try {
            HttpResponse<String> resp = httpClient.send(req.build(), BodyHandlers.ofString());
            int code = resp.statusCode();
            String body = resp.body() == null ? "" : resp.body();
            return new HookResult(code, body, "", parseDecisionJson(body));
        } catch (IOException e) {
            return HookResult.error("http call failed: " + e.getMessage());
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Map<String, Object> parseDecisionJson(String body) {
        if (body == null || body.isBlank()) return Map.of();
        try {
            Object parsed = json.readValue(body, Object.class);
            if (parsed instanceof Map<?, ?> m) {
                Map<String, Object> out = new HashMap<>();
                m.forEach((k, v) -> out.put(String.valueOf(k), v));
                return Collections.unmodifiableMap(out);
            }
        } catch (JsonProcessingException ignored) {
            // non-JSON output is fine — many command hooks just write logs.
        }
        return Map.of();
    }

    private String resolveString(Map<String, Object> config, String key, PluginVariableResolver r) {
        return resolve(stringOrNull(config, key), r);
    }

    private String resolve(String value, PluginVariableResolver r) {
        if (value == null) return null;
        if (r == null) return value;
        return r.resolve(value);
    }

    private String stringOrNull(Map<String, Object> config, String key) {
        Object v = config.get(key);
        return v instanceof String s ? s : null;
    }

    private long numberOrDefault(Map<String, Object> config, String key, long defaultValue) {
        Object v = config.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private List<?> listOrEmpty(Map<String, Object> config, String key) {
        Object v = config.get(key);
        return v instanceof List<?> l ? l : List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> stringMapOrEmpty(Map<String, Object> config, String key) {
        Object v = config.get(key);
        if (!(v instanceof Map<?, ?> m)) return Map.of();
        Map<String, String> out = new HashMap<>();
        m.forEach((k, val) -> out.put(String.valueOf(k), val == null ? "" : String.valueOf(val)));
        return out;
    }

    /**
     * Result of one hook execution.
     *
     * @param exitCode process exit (or HTTP status); 0 = success
     * @param rawOutput raw stdout / response body (truncated by the caller if too large)
     * @param errorOutput stderr (command only); empty for http
     * @param decision parsed JSON object from rawOutput when applicable; never null
     */
    public record HookResult(
            int exitCode, String rawOutput, String errorOutput, Map<String, Object> decision) {
        public HookResult {
            decision = decision == null ? Map.of() : Map.copyOf(decision);
            rawOutput = rawOutput == null ? "" : rawOutput;
            errorOutput = errorOutput == null ? "" : errorOutput;
        }

        public static HookResult empty() {
            return new HookResult(0, "", "", Map.of());
        }

        public static HookResult error(String message) {
            return new HookResult(1, "", message, Map.of());
        }
    }
}
