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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.plugin.PluginComponent.HookComponent.HookAction;
import io.kairo.plugin.variable.PluginVariableResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HookExecutorTest {

    private final HookExecutor executor = new HookExecutor();

    @Test
    void commandRunsAndReceivesEventJsonOnStdin(@TempDir Path tmp) throws Exception {
        Path script = tmp.resolve("echo-stdin.sh");
        Files.writeString(script, "#!/usr/bin/env bash\ncat\n");
        script.toFile().setExecutable(true);

        var action = new HookAction("command", Map.of("command", script.toString(), "timeout", 10));
        var result =
                executor.execute(action, Map.of("toolName", "Bash", "input", "ls"), null)
                        .block(Duration.ofSeconds(15));

        assertThat(result).isNotNull();
        assertThat(result.exitCode()).isZero();
        assertThat(result.rawOutput()).contains("toolName").contains("Bash");
    }

    @Test
    void commandHonoursTimeout(@TempDir Path tmp) throws Exception {
        Path script = tmp.resolve("sleep.sh");
        Files.writeString(script, "#!/usr/bin/env bash\nsleep 5\n");
        script.toFile().setExecutable(true);

        var action = new HookAction("command", Map.of("command", script.toString(), "timeout", 1));
        var result = executor.execute(action, Map.of(), null).block(Duration.ofSeconds(5));

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.errorOutput()).contains("timed out");
    }

    @Test
    void commandShellModeExpandsArgsInline(@TempDir Path tmp) throws Exception {
        // shell mode joins command + args into a single -c string
        var action =
                new HookAction(
                        "command",
                        Map.of(
                                "command",
                                "echo",
                                "args",
                                java.util.List.of("hello", "world"),
                                "shell",
                                "bash",
                                "timeout",
                                10));
        var result = executor.execute(action, Map.of(), null).block(Duration.ofSeconds(15));
        assertThat(result.exitCode()).isZero();
        assertThat(result.rawOutput()).contains("hello world");
    }

    @Test
    void commandResolvesVariables(@TempDir Path tmp) throws Exception {
        Path script = tmp.resolve("hi.sh");
        Files.writeString(script, "#!/usr/bin/env bash\necho \"hi $PLUGIN_NAME\"\n");
        script.toFile().setExecutable(true);

        var resolver = new PluginVariableResolver(tmp, tmp, tmp).with("PLUGIN_NAME", "kairo");
        var action =
                new HookAction(
                        "command",
                        Map.of(
                                "command", "${KAIRO_PLUGIN_ROOT}/hi.sh",
                                "shell", "bash",
                                "timeout", 10));
        var result = executor.execute(action, Map.of(), resolver).block(Duration.ofSeconds(15));
        assertThat(result.exitCode()).isZero();
    }

    @Test
    void commandParsesJsonStdoutAsDecision(@TempDir Path tmp) throws Exception {
        Path script = tmp.resolve("decide.sh");
        Files.writeString(
                script,
                "#!/usr/bin/env bash\necho '{\"continue\": false, \"reason\": \"blocked\"}'\n");
        script.toFile().setExecutable(true);

        var action = new HookAction("command", Map.of("command", script.toString(), "timeout", 10));
        var result = executor.execute(action, Map.of(), null).block(Duration.ofSeconds(15));

        assertThat(result.exitCode()).isZero();
        assertThat(result.decision()).containsEntry("continue", false);
        assertThat(result.decision()).containsEntry("reason", "blocked");
    }

    @Test
    void commandWithMissingFieldReturnsError() {
        var action = new HookAction("command", Map.of());
        var result = executor.execute(action, Map.of(), null).block(Duration.ofSeconds(5));
        assertThat(result.errorOutput()).contains("missing 'command'");
    }

    @Test
    void httpWithMissingUrlReturnsError() {
        var action = new HookAction("http", Map.of());
        var result = executor.execute(action, Map.of(), null).block(Duration.ofSeconds(5));
        assertThat(result.errorOutput()).contains("missing 'url'");
    }

    @Test
    void promptActionStubReturnsEmpty() {
        var action = new HookAction("prompt", Map.of("prompt", "ok?"));
        var result = executor.execute(action, Map.of(), null).block(Duration.ofSeconds(5));
        assertThat(result.exitCode()).isZero();
        assertThat(result.decision()).isEmpty();
    }

    @Test
    void agentActionStubReturnsEmpty() {
        var action = new HookAction("agent", Map.of("prompt", "verify"));
        var result = executor.execute(action, Map.of(), null).block(Duration.ofSeconds(5));
        assertThat(result.exitCode()).isZero();
    }

    @Test
    void mcpToolActionStubReturnsEmpty() {
        var action = new HookAction("mcp_tool", Map.of("server", "x", "tool", "y"));
        var result = executor.execute(action, Map.of(), null).block(Duration.ofSeconds(5));
        assertThat(result.exitCode()).isZero();
    }

    @Test
    void unknownTypeReturnsNoOpResult() {
        var action = new HookAction("bogus", Map.of());
        var result = executor.execute(action, Map.of(), null).block(Duration.ofSeconds(5));
        assertThat(result).isNotNull();
        assertThat(result.exitCode()).isZero();
    }
}
