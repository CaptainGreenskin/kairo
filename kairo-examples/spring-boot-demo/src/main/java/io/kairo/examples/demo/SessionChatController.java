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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller demonstrating multi-turn session-based chat with Kairo.
 *
 * <p>Maintains an in-memory conversation history per session, allowing clients to
 * carry context across multiple requests. Each session accumulates user and assistant
 * messages, which are sent as the full conversation history on every model call.
 *
 * <p>This pattern is useful for building stateful chatbots where the model needs
 * prior context to generate coherent multi-turn responses.
 *
 * <p>Usage:
 * <pre>{@code
 * # Start a new session
 * curl -X POST http://localhost:8080/session/chat \
 *   -H "Content-Type: application/json" \
 *   -d '{"message": "My name is Alice"}'
 *
 * # Continue the same session
 * curl -X POST http://localhost:8080/session/chat \
 *   -H "Content-Type: application/json" \
 *   -d '{"sessionId": "<id-from-above>", "message": "What is my name?"}'
 *
 * # View session history
 * curl http://localhost:8080/session/<id>/history
 *
 * # Clear a session
 * curl -X DELETE http://localhost:8080/session/<id>
 * }</pre>
 */
@RestController
@RequestMapping("/session")
public class SessionChatController {

    private final ModelProvider modelProvider;

    /** In-memory session storage: sessionId -> ordered list of conversation messages. */
    private final ConcurrentHashMap<String, List<Msg>> sessions = new ConcurrentHashMap<>();

    public SessionChatController(ModelProvider modelProvider) {
        this.modelProvider = modelProvider;
    }

    /**
     * Send a message within a session, creating a new session if none is provided.
     *
     * <p>Appends the user message to the session history, calls the model with the
     * full conversation context, and appends the assistant response. If no
     * {@code sessionId} is provided in the request, a new UUID-based session is created.
     *
     * @param request the chat request containing an optional sessionId and the message
     * @return the assistant reply along with the sessionId for subsequent calls
     */
    @PostMapping("/chat")
    public ResponseEntity<SessionChatResponse> chat(@RequestBody SessionChatRequest request) {
        String sessionId = (request.sessionId() != null && !request.sessionId().isBlank())
                ? request.sessionId()
                : UUID.randomUUID().toString();

        List<Msg> history = sessions.computeIfAbsent(
                sessionId, k -> Collections.synchronizedList(new ArrayList<>()));

        // Append user message
        Msg userMsg = Msg.of(MsgRole.USER, request.message());
        history.add(userMsg);

        // Build config
        ModelConfig config = ModelConfig.builder()
                .model(modelProvider.name().equals("anthropic")
                        ? ModelConfig.DEFAULT_MODEL
                        : "qwen-plus")
                .maxTokens(ModelConfig.DEFAULT_MAX_TOKENS)
                .temperature(0.7)
                .systemPrompt("You are a helpful assistant. Remember the conversation context.")
                .build();

        // Call model with full history
        ModelResponse response = modelProvider.call(List.copyOf(history), config).block();

        String replyText;
        if (response != null && response.contents() != null) {
            replyText = response.contents().stream()
                    .filter(Content.TextContent.class::isInstance)
                    .map(Content.TextContent.class::cast)
                    .map(Content.TextContent::text)
                    .findFirst()
                    .orElse("No response");
        } else {
            replyText = "No response";
        }

        // Append assistant message
        Msg assistantMsg = Msg.of(MsgRole.ASSISTANT, replyText);
        history.add(assistantMsg);

        return ResponseEntity.ok(new SessionChatResponse(sessionId, replyText));
    }

    /**
     * Retrieve the full message history for a session.
     *
     * @param id the session identifier
     * @return the ordered list of messages, or 404 if the session does not exist
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<List<MessageView>> getHistory(@PathVariable String id) {
        List<Msg> history = sessions.get(id);
        if (history == null) {
            return ResponseEntity.notFound().build();
        }

        List<MessageView> views;
        synchronized (history) {
            views = history.stream()
                    .map(msg -> new MessageView(msg.role().name().toLowerCase(), msg.text()))
                    .toList();
        }
        return ResponseEntity.ok(views);
    }

    /**
     * Delete a session and its conversation history.
     *
     * @param id the session identifier
     * @return 200 with confirmation, or 404 if the session does not exist
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteSession(@PathVariable String id) {
        List<Msg> removed = sessions.remove(id);
        if (removed == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("status", "deleted", "sessionId", id));
    }

    /** Request body for session-based chat. */
    public record SessionChatRequest(String sessionId, String message) {}

    /** Response containing the session ID and the assistant's reply. */
    public record SessionChatResponse(String sessionId, String reply) {}

    /** Simplified view of a message for the history endpoint. */
    public record MessageView(String role, String text) {}
}
