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

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.plugin.PluginComponent.HookComponent.HookAction;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class PromptHookActionHandlerTest {

    @Test
    void sendsPromptToModelAndReturnsTextResponse() {
        AtomicReference<List<Msg>> captured = new AtomicReference<>();
        AtomicReference<ModelConfig> capturedConfig = new AtomicReference<>();
        var provider = new RecordingModelProvider("Hello!", captured, capturedConfig);
        var defaultConfig =
                ModelConfig.builder().model("default-model").systemPrompt("be terse").build();
        var handler = new PromptHookActionHandler(provider, defaultConfig);

        var action = new HookAction("prompt", Map.of("prompt", "Greet me"));
        var result =
                handler.execute(action, Map.of("name", "alice"), null).block(Duration.ofSeconds(5));

        assertThat(result.exitCode()).isZero();
        assertThat(result.rawOutput()).isEqualTo("Hello!");
        // Default config preserved when action does not override.
        assertThat(capturedConfig.get().model()).isEqualTo("default-model");
        // Prompt was forwarded as the user message.
        assertThat(captured.get().get(0).text()).contains("Greet me");
    }

    @Test
    void substitutesArgumentsPlaceholderWithEventPayload() {
        AtomicReference<List<Msg>> captured = new AtomicReference<>();
        var provider = new RecordingModelProvider("ok", captured, new AtomicReference<>());
        var handler =
                new PromptHookActionHandler(provider, ModelConfig.builder().model("m").build());

        var action = new HookAction("prompt", Map.of("prompt", "Process: $ARGUMENTS"));
        handler.execute(action, Map.of("file", "a.txt"), null).block(Duration.ofSeconds(5));

        assertThat(captured.get().get(0).text()).contains("\"file\":\"a.txt\"");
    }

    @Test
    void modelOverrideOnActionTakesPrecedence() {
        AtomicReference<ModelConfig> capturedConfig = new AtomicReference<>();
        var provider = new RecordingModelProvider("ok", new AtomicReference<>(), capturedConfig);
        var defaultConfig = ModelConfig.builder().model("default-model").build();
        var handler = new PromptHookActionHandler(provider, defaultConfig);

        var action = new HookAction("prompt", Map.of("prompt", "Hi", "model", "custom-model"));
        handler.execute(action, Map.of(), null).block(Duration.ofSeconds(5));

        assertThat(capturedConfig.get().model()).isEqualTo("custom-model");
    }

    @Test
    void parsesJsonResponseIntoDecisionMap() {
        var provider =
                new RecordingModelProvider(
                        "{\"continue\": false, \"reason\": \"blocked\"}",
                        new AtomicReference<>(),
                        new AtomicReference<>());
        var handler =
                new PromptHookActionHandler(provider, ModelConfig.builder().model("m").build());
        var result =
                handler.execute(new HookAction("prompt", Map.of("prompt", "ok?")), Map.of(), null)
                        .block(Duration.ofSeconds(5));
        assertThat(result.decision()).containsEntry("continue", false);
        assertThat(result.decision()).containsEntry("reason", "blocked");
    }

    @Test
    void plainTextResponseLeavesDecisionEmpty() {
        var provider =
                new RecordingModelProvider(
                        "just text", new AtomicReference<>(), new AtomicReference<>());
        var handler =
                new PromptHookActionHandler(provider, ModelConfig.builder().model("m").build());
        var result =
                handler.execute(new HookAction("prompt", Map.of("prompt", "ok?")), Map.of(), null)
                        .block(Duration.ofSeconds(5));
        assertThat(result.rawOutput()).isEqualTo("just text");
        assertThat(result.decision()).isEmpty();
    }

    @Test
    void missingPromptReturnsError() {
        var provider =
                new RecordingModelProvider("x", new AtomicReference<>(), new AtomicReference<>());
        var handler =
                new PromptHookActionHandler(provider, ModelConfig.builder().model("m").build());
        var result =
                handler.execute(new HookAction("prompt", Map.of()), Map.of(), null)
                        .block(Duration.ofSeconds(5));
        assertThat(result.errorOutput()).contains("missing 'prompt'");
    }

    @Test
    void modelErrorIsSurfacedAsHookError() {
        var provider =
                new ModelProvider() {
                    @Override
                    public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
                        return Mono.error(new RuntimeException("upstream down"));
                    }

                    @Override
                    public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
                        return Flux.empty();
                    }

                    @Override
                    public String name() {
                        return "stub";
                    }
                };
        var handler =
                new PromptHookActionHandler(provider, ModelConfig.builder().model("m").build());
        var result =
                handler.execute(new HookAction("prompt", Map.of("prompt", "x")), Map.of(), null)
                        .block(Duration.ofSeconds(5));
        assertThat(result.errorOutput()).contains("upstream down");
    }

    @Test
    void typeIsPrompt() {
        var handler =
                new PromptHookActionHandler(
                        new RecordingModelProvider(
                                "x", new AtomicReference<>(), new AtomicReference<>()),
                        ModelConfig.builder().model("m").build());
        assertThat(handler.type()).isEqualTo("prompt");
    }

    /** Captures inputs and emits a configured text response. */
    private static final class RecordingModelProvider implements ModelProvider {
        private final String responseText;
        private final AtomicReference<List<Msg>> captured;
        private final AtomicReference<ModelConfig> capturedConfig;

        RecordingModelProvider(
                String responseText,
                AtomicReference<List<Msg>> captured,
                AtomicReference<ModelConfig> capturedConfig) {
            this.responseText = responseText;
            this.captured = captured;
            this.capturedConfig = capturedConfig;
        }

        @Override
        public Mono<ModelResponse> call(List<Msg> messages, ModelConfig config) {
            captured.set(messages);
            capturedConfig.set(config);
            return Mono.just(
                    new ModelResponse(
                            "test-id",
                            List.of(new Content.TextContent(responseText)),
                            null,
                            null,
                            "stub"));
        }

        @Override
        public Flux<ModelResponse> stream(List<Msg> messages, ModelConfig config) {
            return Flux.empty();
        }

        @Override
        public String name() {
            return "stub";
        }
    }
}
