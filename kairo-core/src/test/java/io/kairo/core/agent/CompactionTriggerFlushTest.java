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
package io.kairo.core.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import io.kairo.api.agent.AgentConfig;
import io.kairo.api.context.ContextManager;
import io.kairo.api.memory.MemoryEntry;
import io.kairo.api.memory.MemoryScope;
import io.kairo.api.memory.MemoryStore;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.core.context.TokenBudgetManager;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.model.ModelFallbackManager;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CompactionTriggerFlushTest {

    private ContextManager contextManager;
    private ReActLoop reactLoop;
    private MemoryStore memoryStore;

    @BeforeEach
    void setUp() {
        contextManager = mock(ContextManager.class);
        memoryStore = mock(MemoryStore.class);
        MemoryEntry dummyEntry =
                new MemoryEntry(
                        "dummy", "dummy", MemoryScope.SESSION, Instant.now(), List.of(), true);
        when(memoryStore.save(any(MemoryEntry.class))).thenReturn(Mono.just(dummyEntry));

        // Create a real ReActLoop to avoid Mockito inline-mock issues with Java 25
        ModelProvider modelProvider = mock(ModelProvider.class);
        ToolExecutor toolExecutor = mock(ToolExecutor.class);
        AgentConfig config =
                AgentConfig.builder()
                        .name("test-agent")
                        .modelProvider(modelProvider)
                        .modelName("test-model")
                        .maxIterations(10)
                        .tokenBudget(200_000)
                        .build();
        ErrorRecoveryStrategy errorRecovery =
                new ErrorRecoveryStrategy(modelProvider, null, new ModelFallbackManager(List.of()));
        ReActLoopContext ctx =
                new ReActLoopContext(
                        "agent-1",
                        "test-agent",
                        config,
                        new DefaultHookChain(),
                        null,
                        toolExecutor,
                        errorRecovery,
                        new TokenBudgetManager(200_000, 8_096),
                        new GracefulShutdownManager(),
                        null);
        ModelConfig modelConfig =
                ModelConfig.builder()
                        .model("test-model")
                        .maxTokens(4096)
                        .temperature(0.7)
                        .tools(List.of())
                        .build();
        reactLoop =
                new ReActLoop(
                        ctx,
                        new AtomicBoolean(false),
                        new AtomicInteger(0),
                        new AtomicLong(0),
                        () -> modelConfig);
    }

    private Msg verbatimMsg(String text) {
        return Msg.builder()
                .role(MsgRole.USER)
                .addContent(new Content.TextContent(text))
                .verbatimPreserved(true)
                .build();
    }

    private Msg normalMsg(String text) {
        return Msg.builder()
                .role(MsgRole.ASSISTANT)
                .addContent(new Content.TextContent(text))
                .build();
    }

    @Test
    @DisplayName("Flush saves verbatim messages to MemoryStore before compaction")
    void flushSavesVerbatimMessages() {
        when(contextManager.needsCompaction(anyList())).thenReturn(true);
        when(contextManager.compactMessages(anyList()))
                .thenReturn(Mono.just(List.of(normalMsg("summary"))));

        Msg v1 = verbatimMsg("important fact 1");
        Msg v2 = verbatimMsg("important fact 2");
        Msg n1 = normalMsg("regular message");
        List<Msg> history = List.of(v1, v2, n1);

        CompactionTrigger trigger =
                new CompactionTrigger(contextManager, reactLoop, memoryStore, null);

        StepVerifier.create(trigger.checkAndCompact(history)).expectNext(true).verifyComplete();

        ArgumentCaptor<MemoryEntry> captor = ArgumentCaptor.forClass(MemoryEntry.class);
        verify(memoryStore, times(2)).save(captor.capture());

        List<MemoryEntry> saved = captor.getAllValues();
        assertEquals("important fact 1", saved.get(0).content());
        assertEquals("important fact 2", saved.get(1).content());
        assertEquals(MemoryScope.SESSION, saved.get(0).scope());
        assertTrue(saved.get(0).verbatim());
        assertTrue(saved.get(0).tags().contains("compaction-flush"));
    }

    @Test
    @DisplayName("Skips non-verbatim messages during flush")
    void skipsNonVerbatimMessages() {
        when(contextManager.needsCompaction(anyList())).thenReturn(true);
        when(contextManager.compactMessages(anyList()))
                .thenReturn(Mono.just(List.of(normalMsg("summary"))));

        List<Msg> history = List.of(normalMsg("msg1"), normalMsg("msg2"), normalMsg("msg3"));

        CompactionTrigger trigger =
                new CompactionTrigger(contextManager, reactLoop, memoryStore, null);

        StepVerifier.create(trigger.checkAndCompact(history)).expectNext(true).verifyComplete();

        verify(memoryStore, never()).save(any());
    }

    @Test
    @DisplayName("Handles null memoryStore gracefully — no NPE, no-op")
    void handlesNullMemoryStore() {
        when(contextManager.needsCompaction(anyList())).thenReturn(true);
        when(contextManager.compactMessages(anyList()))
                .thenReturn(Mono.just(List.of(normalMsg("summary"))));

        List<Msg> history = List.of(verbatimMsg("important"));

        // null memoryStore via old 2-arg constructor
        CompactionTrigger trigger = new CompactionTrigger(contextManager, reactLoop);

        StepVerifier.create(trigger.checkAndCompact(history)).expectNext(true).verifyComplete();

        // No memory store interaction — no NPE
        verifyNoInteractions(memoryStore);
    }

    @Test
    @DisplayName("Flush happens BEFORE compaction call")
    void flushHappensBeforeCompaction() {
        when(contextManager.needsCompaction(anyList())).thenReturn(true);
        when(contextManager.compactMessages(anyList()))
                .thenReturn(Mono.just(List.of(normalMsg("summary"))));

        List<Msg> history = List.of(verbatimMsg("preserve me"));

        CompactionTrigger trigger =
                new CompactionTrigger(contextManager, reactLoop, memoryStore, null);

        StepVerifier.create(trigger.checkAndCompact(history)).expectNext(true).verifyComplete();

        // Verify order: save is called before compactMessages
        InOrder inOrder = inOrder(memoryStore, contextManager);
        inOrder.verify(memoryStore).save(any(MemoryEntry.class));
        inOrder.verify(contextManager).compactMessages(anyList());
    }

    @Test
    @DisplayName("Custom importancePredicate overrides default verbatim check")
    void customImportancePredicateWorks() {
        when(contextManager.needsCompaction(anyList())).thenReturn(true);
        when(contextManager.compactMessages(anyList()))
                .thenReturn(Mono.just(List.of(normalMsg("summary"))));

        Msg assistantMsg = normalMsg("assistant response");
        Msg verbatimButIgnored = verbatimMsg("verbatim but ignored by custom predicate");

        // Custom predicate: only flush USER role messages
        CompactionTrigger trigger =
                new CompactionTrigger(
                        contextManager, reactLoop, memoryStore, msg -> msg.role() == MsgRole.USER);

        Msg realUserMsg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("user says hello"))
                        .build();

        List<Msg> history = List.of(realUserMsg, assistantMsg, verbatimButIgnored);

        StepVerifier.create(trigger.checkAndCompact(history)).expectNext(true).verifyComplete();

        // Both USER-role messages should be saved (realUserMsg and verbatimButIgnored)
        ArgumentCaptor<MemoryEntry> captor = ArgumentCaptor.forClass(MemoryEntry.class);
        verify(memoryStore, times(2)).save(captor.capture());

        List<String> contents = captor.getAllValues().stream().map(MemoryEntry::content).toList();
        assertTrue(contents.contains("user says hello"));
        assertTrue(contents.contains("verbatim but ignored by custom predicate"));
    }

    @Test
    @DisplayName("Flush error handling: save failure does not prevent compaction")
    void flushErrorDoesNotPreventCompaction() {
        when(contextManager.needsCompaction(anyList())).thenReturn(true);
        when(contextManager.compactMessages(anyList()))
                .thenReturn(Mono.just(List.of(normalMsg("summary"))));

        // First save fails, second succeeds
        MemoryEntry dummyEntry =
                new MemoryEntry(
                        "dummy", "dummy", MemoryScope.SESSION, Instant.now(), List.of(), true);
        when(memoryStore.save(any(MemoryEntry.class)))
                .thenReturn(Mono.error(new RuntimeException("Storage failure")))
                .thenReturn(Mono.just(dummyEntry));

        Msg v1 = verbatimMsg("will fail to save");
        Msg v2 = verbatimMsg("will succeed");
        List<Msg> history = List.of(v1, v2);

        CompactionTrigger trigger =
                new CompactionTrigger(contextManager, reactLoop, memoryStore, null);

        // Compaction should still complete successfully despite save error
        StepVerifier.create(trigger.checkAndCompact(history)).expectNext(true).verifyComplete();

        // Both saves were attempted
        verify(memoryStore, times(2)).save(any(MemoryEntry.class));
        // Compaction was still called
        verify(contextManager).compactMessages(anyList());
    }

    @Test
    @DisplayName("All flush saves complete before compactMessages is called")
    void allFlushSavesCompleteBeforeCompaction() {
        when(contextManager.needsCompaction(anyList())).thenReturn(true);
        when(contextManager.compactMessages(anyList()))
                .thenReturn(Mono.just(List.of(normalMsg("summary"))));

        Msg v1 = verbatimMsg("fact A");
        Msg v2 = verbatimMsg("fact B");
        Msg v3 = verbatimMsg("fact C");
        List<Msg> history = List.of(v1, v2, v3);

        CompactionTrigger trigger =
                new CompactionTrigger(contextManager, reactLoop, memoryStore, null);

        StepVerifier.create(trigger.checkAndCompact(history)).expectNext(true).verifyComplete();

        // All 3 saves must happen before compactMessages
        InOrder inOrder = inOrder(memoryStore, contextManager);
        inOrder.verify(memoryStore, times(3)).save(any(MemoryEntry.class));
        inOrder.verify(contextManager).compactMessages(anyList());
    }

    @Test
    @DisplayName("Messages with null or blank text are skipped during flush")
    void nullAndBlankTextMessagesSkipped() {
        when(contextManager.needsCompaction(anyList())).thenReturn(true);
        when(contextManager.compactMessages(anyList()))
                .thenReturn(Mono.just(List.of(normalMsg("summary"))));

        // A verbatim message with no TextContent — text() returns ""
        Msg noTextMsg = Msg.builder().role(MsgRole.USER).verbatimPreserved(true).build();
        // A verbatim message with blank text
        Msg blankTextMsg =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("   "))
                        .verbatimPreserved(true)
                        .build();
        // A normal verbatim message with valid text
        Msg validMsg = verbatimMsg("valid content");

        List<Msg> history = List.of(noTextMsg, blankTextMsg, validMsg);

        CompactionTrigger trigger =
                new CompactionTrigger(contextManager, reactLoop, memoryStore, null);

        StepVerifier.create(trigger.checkAndCompact(history)).expectNext(true).verifyComplete();

        // Only the valid message should be saved
        ArgumentCaptor<MemoryEntry> captor = ArgumentCaptor.forClass(MemoryEntry.class);
        verify(memoryStore, times(1)).save(captor.capture());
        assertEquals("valid content", captor.getValue().content());
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    @DisplayName("All flushed MemoryEntries have scope=SESSION")
    void testFlushedEntryScopeAlwaysSession() {
        when(contextManager.needsCompaction(anyList())).thenReturn(true);
        when(contextManager.compactMessages(anyList()))
                .thenReturn(Mono.just(List.of(normalMsg("summary"))));

        Msg v1 = verbatimMsg("fact one");
        Msg v2 = verbatimMsg("fact two");
        Msg v3 = verbatimMsg("fact three");
        List<Msg> history = List.of(v1, v2, v3);

        CompactionTrigger trigger =
                new CompactionTrigger(contextManager, reactLoop, memoryStore, null);

        StepVerifier.create(trigger.checkAndCompact(history)).expectNext(true).verifyComplete();

        ArgumentCaptor<MemoryEntry> captor = ArgumentCaptor.forClass(MemoryEntry.class);
        verify(memoryStore, times(3)).save(captor.capture());

        captor.getAllValues()
                .forEach(
                        entry ->
                                assertEquals(
                                        MemoryScope.SESSION,
                                        entry.scope(),
                                        "Every flushed entry must have SESSION scope"));
    }

    @Test
    @DisplayName("All flushed MemoryEntries have verbatim=true")
    void testFlushedEntryVerbatimAlwaysTrue() {
        when(contextManager.needsCompaction(anyList())).thenReturn(true);
        when(contextManager.compactMessages(anyList()))
                .thenReturn(Mono.just(List.of(normalMsg("summary"))));

        Msg v1 = verbatimMsg("important A");
        Msg v2 = verbatimMsg("important B");
        List<Msg> history = List.of(v1, v2);

        CompactionTrigger trigger =
                new CompactionTrigger(contextManager, reactLoop, memoryStore, null);

        StepVerifier.create(trigger.checkAndCompact(history)).expectNext(true).verifyComplete();

        ArgumentCaptor<MemoryEntry> captor = ArgumentCaptor.forClass(MemoryEntry.class);
        verify(memoryStore, times(2)).save(captor.capture());

        captor.getAllValues()
                .forEach(
                        entry ->
                                assertTrue(
                                        entry.verbatim(),
                                        "Every flushed entry must have verbatim=true"));
    }

    @Test
    @DisplayName("Empty conversation history triggers no flush and no errors")
    void testEmptyHistoryNoFlush() {
        when(contextManager.needsCompaction(anyList())).thenReturn(true);
        when(contextManager.compactMessages(anyList())).thenReturn(Mono.just(List.of()));

        List<Msg> history = List.of();

        CompactionTrigger trigger =
                new CompactionTrigger(contextManager, reactLoop, memoryStore, null);

        StepVerifier.create(trigger.checkAndCompact(history)).expectNext(true).verifyComplete();

        // No save calls since no important messages exist
        verify(memoryStore, never()).save(any());
    }
}
