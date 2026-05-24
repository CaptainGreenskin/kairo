/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.hook.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.plugin.PluginComponent.HookComponent.HookAction;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AgentHookActionHandlerTest {

    @Test
    void invokesRunnerWithResolvedPromptAndModelHint() {
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        AtomicReference<String> capturedModel = new AtomicReference<>();
        var handler =
                new AgentHookActionHandler(
                        (prompt, model) -> {
                            capturedPrompt.set(prompt);
                            capturedModel.set(model);
                            return Mono.just("verified");
                        });

        var action =
                new HookAction(
                        "agent",
                        Map.of("prompt", "Verify $ARGUMENTS", "model", "claude-sonnet-4-6"));
        var result =
                handler.execute(action, Map.of("file", "x.java"), null)
                        .block(Duration.ofSeconds(5));

        assertThat(result.exitCode()).isZero();
        assertThat(result.rawOutput()).isEqualTo("verified");
        assertThat(capturedPrompt.get()).contains("Verify").contains("\"file\":\"x.java\"");
        assertThat(capturedModel.get()).isEqualTo("claude-sonnet-4-6");
    }

    @Test
    void parsesJsonResponseIntoDecision() {
        var handler =
                new AgentHookActionHandler((prompt, model) -> Mono.just("{\"approved\":true}"));
        var result =
                handler.execute(new HookAction("agent", Map.of("prompt", "x")), Map.of(), null)
                        .block(Duration.ofSeconds(5));
        assertThat(result.decision()).containsEntry("approved", true);
    }

    @Test
    void runnerErrorIsSurfacedAsHookError() {
        var handler =
                new AgentHookActionHandler(
                        (prompt, model) -> Mono.error(new RuntimeException("agent loop crashed")));
        var result =
                handler.execute(new HookAction("agent", Map.of("prompt", "x")), Map.of(), null)
                        .block(Duration.ofSeconds(5));
        assertThat(result.errorOutput()).contains("agent loop crashed");
    }

    @Test
    void missingPromptReturnsError() {
        var handler = new AgentHookActionHandler((prompt, model) -> Mono.just("ok"));
        var result =
                handler.execute(new HookAction("agent", Map.of()), Map.of(), null)
                        .block(Duration.ofSeconds(5));
        assertThat(result.errorOutput()).contains("missing 'prompt'");
    }

    @Test
    void typeIsAgent() {
        var handler = new AgentHookActionHandler((prompt, model) -> Mono.just(""));
        assertThat(handler.type()).isEqualTo("agent");
    }
}
