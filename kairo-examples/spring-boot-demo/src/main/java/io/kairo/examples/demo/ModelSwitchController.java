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
package io.kairo.examples.demo;

import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.core.model.ProviderPresets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller demonstrating dynamic model provider switching with Kairo.
 *
 * <p>Injects the auto-configured default {@link ModelProvider} and allows clients to route requests
 * to different providers (OpenAI, Anthropic, Qwen, GLM, DeepSeek) at runtime. Additional providers
 * are created on-demand using {@link ProviderPresets} factory methods and cached for reuse.
 *
 * <p>This pattern is useful for applications that need to compare outputs across different LLM
 * providers or route specific tasks to specialized models.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * # Chat using the default provider
 * curl -X POST http://localhost:8080/models/chat \
 *   -H "Content-Type: application/json" \
 *   -d '{"message": "What is Kairo?"}'
 *
 * # Chat using a specific provider
 * curl -X POST "http://localhost:8080/models/chat?provider=anthropic" \
 *   -H "Content-Type: application/json" \
 *   -d '{"message": "What is Kairo?"}'
 *
 * # List available providers
 * curl http://localhost:8080/models/available
 * }</pre>
 */
@RestController
@RequestMapping("/models")
public class ModelSwitchController {

    private static final Logger log = LoggerFactory.getLogger(ModelSwitchController.class);

    /** Known provider names that can be created via {@link ProviderPresets}. */
    private static final Set<String> KNOWN_PROVIDERS =
            Set.of("anthropic", "openai", "qwen", "glm", "deepseek");

    /** Cached provider instances keyed by provider name. */
    private final ConcurrentHashMap<String, ModelProvider> providers = new ConcurrentHashMap<>();

    public ModelSwitchController(ModelProvider modelProvider) {
        // Register the auto-configured default provider
        providers.put(modelProvider.name(), modelProvider);
        log.info("Registered default model provider: {}", modelProvider.name());
    }

    /**
     * Chat with a specific model provider.
     *
     * <p>If no {@code provider} parameter is given, uses the auto-configured default. Providers are
     * resolved from the cache or created on-demand via {@link ProviderPresets} if the corresponding
     * API key environment variable is set.
     *
     * @param provider the provider name (e.g. "openai", "anthropic", "qwen")
     * @param request the chat request body
     * @return the model's reply along with the provider name used
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(
            @RequestParam(required = false) String provider,
            @RequestBody ModelChatRequest request) {

        // Resolve provider
        ModelProvider selectedProvider;
        if (provider == null || provider.isBlank()) {
            // Use the first (default) registered provider
            selectedProvider = providers.values().iterator().next();
        } else {
            selectedProvider = providers.get(provider);
            if (selectedProvider == null) {
                // Try to create it on-demand
                selectedProvider = tryCreateProvider(provider);
                if (selectedProvider == null) {
                    return ResponseEntity.badRequest()
                            .body(
                                    Map.of(
                                            "error",
                                                    "Unknown or unconfigured provider: " + provider,
                                            "available", providers.keySet(),
                                            "hint",
                                                    "Set the appropriate API key env var to enable a provider. "
                                                            + "Known providers: "
                                                            + KNOWN_PROVIDERS));
                }
            }
        }

        String modelName = resolveModelName(selectedProvider.name());
        ModelConfig config =
                ModelConfig.builder()
                        .model(modelName)
                        .maxTokens(ModelConfig.DEFAULT_MAX_TOKENS)
                        .temperature(0.7)
                        .systemPrompt("You are a helpful assistant.")
                        .build();

        Msg userMsg = Msg.of(MsgRole.USER, request.message());
        ModelResponse response = selectedProvider.call(List.of(userMsg), config).block();

        String replyText;
        if (response != null && response.contents() != null) {
            replyText =
                    response.contents().stream()
                            .filter(Content.TextContent.class::isInstance)
                            .map(Content.TextContent.class::cast)
                            .map(Content.TextContent::text)
                            .findFirst()
                            .orElse("No response");
        } else {
            replyText = "No response";
        }

        return ResponseEntity.ok(
                Map.of(
                        "provider", selectedProvider.name(),
                        "model", modelName,
                        "reply", replyText));
    }

    /**
     * List all currently available (registered) provider names.
     *
     * @return the set of provider names that can be used in {@code /models/chat?provider=...}
     */
    @GetMapping("/available")
    public ResponseEntity<Map<String, Object>> availableProviders() {
        return ResponseEntity.ok(Map.of("providers", providers.keySet(), "known", KNOWN_PROVIDERS));
    }

    /**
     * Attempt to create a provider on-demand using {@link ProviderPresets}. Returns null if the
     * required API key environment variable is not set.
     */
    private ModelProvider tryCreateProvider(String name) {
        if (!KNOWN_PROVIDERS.contains(name)) {
            return null;
        }

        String apiKey = resolveApiKeyForProvider(name);
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Cannot create provider '{}': API key env var not set", name);
            return null;
        }

        ModelProvider created =
                switch (name) {
                    case "anthropic" -> ProviderPresets.anthropic(apiKey);
                    case "openai" -> ProviderPresets.openai(apiKey);
                    case "qwen" -> ProviderPresets.qwen(apiKey);
                    case "glm" -> ProviderPresets.glm(apiKey);
                    case "deepseek" -> ProviderPresets.deepseek(apiKey);
                    default -> null;
                };

        if (created != null) {
            providers.put(name, created);
            log.info("Created and cached provider '{}' on-demand", name);
        }
        return created;
    }

    /** Resolve the API key environment variable for a given provider name. */
    private static String resolveApiKeyForProvider(String provider) {
        return switch (provider) {
            case "anthropic" -> System.getenv("ANTHROPIC_API_KEY");
            case "openai" -> System.getenv("OPENAI_API_KEY");
            case "qwen" -> System.getenv("DASHSCOPE_API_KEY");
            case "glm" -> System.getenv("ZHIPU_API_KEY");
            case "deepseek" -> System.getenv("DEEPSEEK_API_KEY");
            default -> null;
        };
    }

    /** Resolve a sensible default model name for each provider. */
    private static String resolveModelName(String provider) {
        return switch (provider) {
            case "anthropic" -> ModelConfig.DEFAULT_MODEL;
            case "openai" -> "qwen-plus";
            case "qwen" -> "qwen-plus";
            case "glm" -> "glm-4-plus";
            case "deepseek" -> "deepseek-chat";
            default -> ModelConfig.DEFAULT_MODEL;
        };
    }

    /** Request body for model-switch chat. */
    public record ModelChatRequest(String message) {}
}
