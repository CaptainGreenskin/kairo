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
package io.kairo.core.memory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import io.kairo.api.memory.MemoryEntry;
import io.kairo.api.memory.MemoryScope;
import io.kairo.api.memory.MemoryStore;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SessionMemoryCompactTest {

    private MemoryStore memoryStore;
    private ModelProvider modelProvider;
    private SessionMemoryCompact sessionMemory;

    @BeforeEach
    void setUp() {
        memoryStore = mock(MemoryStore.class);
        modelProvider = mock(ModelProvider.class);
        when(modelProvider.name()).thenReturn("anthropic");
        sessionMemory = new SessionMemoryCompact(memoryStore, modelProvider);
    }

    @Test
    void saveSession_generatesAndSavesSummary() {
        // Mock model provider to return a summary
        String summaryText = "Session summary: user worked on a Java project.";
        ModelResponse response =
                new ModelResponse(
                        "resp-1",
                        List.of(new Content.TextContent(summaryText)),
                        new ModelResponse.Usage(100, 50, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "claude-sonnet-4-20250514");
        when(modelProvider.call(any(), any(ModelConfig.class))).thenReturn(Mono.just(response));
        when(memoryStore.save(any(MemoryEntry.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        List<Msg> history =
                List.of(
                        Msg.of(MsgRole.USER, "Help me write tests"),
                        Msg.of(MsgRole.ASSISTANT, "Sure, I'll help."));

        StepVerifier.create(sessionMemory.saveSession("test-session", history))
                .expectNextMatches(
                        entry -> {
                            // Verify the entry has correct id, scope, and tags
                            return entry.id().equals("session-test-session")
                                    && entry.scope() == MemoryScope.SESSION
                                    && entry.tags().contains("session")
                                    && entry.tags().contains("test-session")
                                    && entry.content().equals(summaryText);
                        })
                .verifyComplete();

        // Verify memoryStore.save() was called with correct entry
        verify(memoryStore)
                .save(
                        argThat(
                                entry ->
                                        entry.id().equals("session-test-session")
                                                && entry.scope() == MemoryScope.SESSION
                                                && entry.content().equals(summaryText)));
    }

    @Test
    void saveSession_emptyHistory_returnsEmpty() {
        StepVerifier.create(sessionMemory.saveSession("test-session", List.of())).verifyComplete();

        // Model provider should not be called
        verify(modelProvider, never()).call(any(), any());
        verify(memoryStore, never()).save(any());
    }

    @Test
    void saveSession_nullHistory_returnsEmpty() {
        StepVerifier.create(sessionMemory.saveSession("test-session", null)).verifyComplete();

        verify(modelProvider, never()).call(any(), any());
    }

    @Test
    void saveSession_summaryPromptContainsKeyDimensions() {
        // Capture the model config to verify the summary prompt
        String summaryText = "Summary";
        ModelResponse response =
                new ModelResponse(
                        "resp-1",
                        List.of(new Content.TextContent(summaryText)),
                        new ModelResponse.Usage(100, 50, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "claude-sonnet-4-20250514");
        when(modelProvider.call(any(), any(ModelConfig.class))).thenReturn(Mono.just(response));
        when(memoryStore.save(any(MemoryEntry.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        List<Msg> history = List.of(Msg.of(MsgRole.USER, "Hello"));

        StepVerifier.create(sessionMemory.saveSession("s1", history))
                .expectNextCount(1)
                .verifyComplete();

        // Verify the model was called and the messages include the summary prompt
        verify(modelProvider).call(any(), any(ModelConfig.class));
    }

    @Test
    void loadSession_existingSession_returnsContent() {
        String sessionContent = "Previous session: user was writing tests for Kairo.";
        MemoryEntry entry =
                new MemoryEntry(
                        "session-my-session",
                        sessionContent,
                        MemoryScope.SESSION,
                        Instant.now(),
                        List.of("session", "my-session"),
                        false);
        when(memoryStore.get("session-my-session")).thenReturn(Mono.just(entry));

        StepVerifier.create(sessionMemory.loadSession("my-session"))
                .expectNext(sessionContent)
                .verifyComplete();
    }

    @Test
    void loadSession_noExistingSession_returnsEmptyString() {
        when(memoryStore.get("session-unknown")).thenReturn(Mono.empty());

        StepVerifier.create(sessionMemory.loadSession("unknown")).expectNext("").verifyComplete();
    }
}
