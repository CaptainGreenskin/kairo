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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.plugin.PluginComponent.HookComponent.HookAction;
import io.kairo.plugin.hook.HookActionHandler;
import io.kairo.plugin.hook.HookExecutor;
import io.kairo.plugin.variable.PluginVariableResolver;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Handles {@code "type": "prompt"} hook actions by sending the prompt (with the event payload
 * substituted into {@code $ARGUMENTS}) to the host's {@link ModelProvider} as a single-shot call.
 * The model's response text becomes the {@link HookExecutor.HookResult#rawOutput()}; if the
 * response is a JSON object it is also parsed into {@code decision}.
 *
 * <p>Configurable fields on the action:
 *
 * <ul>
 *   <li>{@code prompt} — required; the prompt body. {@code $ARGUMENTS} is replaced with the event
 *       payload as JSON, mirroring Claude Code semantics.
 *   <li>{@code model} — optional; passed through to {@link ModelConfig#model()}. Defaults to
 *       whatever the supplied {@link ModelConfig} sets.
 *   <li>{@code timeout} — optional; seconds. Defaults to 30 (matches Claude Code).
 * </ul>
 *
 * <p>The host is responsible for supplying a sensible default {@link ModelConfig} (provider
 * credentials, system prompt, etc.). This handler only overrides {@code model} when the action
 * specifies it.
 */
public final class PromptHookActionHandler implements HookActionHandler {

    private static final long DEFAULT_TIMEOUT = 30;

    private static final Logger log = LoggerFactory.getLogger(PromptHookActionHandler.class);

    private final ModelProvider modelProvider;
    private final ModelConfig defaultConfig;
    private final ObjectMapper json = new ObjectMapper();

    public PromptHookActionHandler(ModelProvider modelProvider, ModelConfig defaultConfig) {
        this.modelProvider = modelProvider;
        this.defaultConfig = defaultConfig;
    }

    @Override
    public String type() {
        return "prompt";
    }

    @Override
    public Mono<HookExecutor.HookResult> execute(
            HookAction action, Map<String, Object> payload, PluginVariableResolver resolver) {
        String promptTemplate = stringOrNull(action.config(), "prompt");
        if (promptTemplate == null || promptTemplate.isBlank()) {
            return Mono.just(HookExecutor.HookResult.error("prompt action missing 'prompt' field"));
        }

        String resolved = resolver == null ? promptTemplate : resolver.resolve(promptTemplate);
        String prompt = resolved.replace("$ARGUMENTS", serialize(payload));

        ModelConfig config = configFor(action);
        long timeout = numberOrDefault(action.config(), "timeout", DEFAULT_TIMEOUT);

        List<Msg> messages = List.of(Msg.of(MsgRole.USER, prompt));

        return modelProvider
                .call(messages, config)
                .timeout(Duration.ofSeconds(timeout))
                .map(
                        resp -> {
                            String text = extractText(resp);
                            return new HookExecutor.HookResult(
                                    0, text, "", parseDecisionJson(text));
                        })
                .onErrorResume(
                        err -> {
                            log.warn(
                                    "prompt hook failed: {}",
                                    err.getMessage() == null
                                            ? err.getClass().getSimpleName()
                                            : err.getMessage());
                            return Mono.just(HookExecutor.HookResult.error(err.getMessage()));
                        });
    }

    private ModelConfig configFor(HookAction action) {
        String overrideModel = stringOrNull(action.config(), "model");
        if (overrideModel == null || overrideModel.isBlank() || defaultConfig == null) {
            return defaultConfig;
        }
        return ModelConfig.builder()
                .model(overrideModel)
                .systemPrompt(defaultConfig.systemPrompt())
                .temperature(defaultConfig.temperature())
                .maxTokens(defaultConfig.maxTokens())
                .build();
    }

    private static String extractText(ModelResponse resp) {
        if (resp == null || resp.contents() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Content c : resp.contents()) {
            if (c instanceof Content.TextContent t) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(t.text());
            }
        }
        return sb.toString();
    }

    private String serialize(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return "";
        try {
            return json.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return payload.toString();
        }
    }

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
            // model returned plain text — that's fine.
        }
        return Map.of();
    }

    private String stringOrNull(Map<String, Object> config, String key) {
        Object v = config.get(key);
        return v instanceof String s ? s : null;
    }

    private long numberOrDefault(Map<String, Object> config, String key, long defaultValue) {
        Object v = config.get(key);
        if (v instanceof Number n) return n.longValue();
        return defaultValue;
    }
}
