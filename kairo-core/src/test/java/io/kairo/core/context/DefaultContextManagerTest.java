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
package io.kairo.core.context;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.context.BoundaryMarker;
import io.kairo.api.context.CompactionConfig;
import io.kairo.api.context.CompactionResult;
import io.kairo.api.context.CompactionStrategy;
import io.kairo.api.context.ContextState;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.core.context.compaction.CompactionPipeline;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DefaultContextManagerTest {

    private DefaultContextManager manager;

    @BeforeEach
    void setUp() {
        manager = new DefaultContextManager();
    }

    private Msg userMsg(String id, String text, int tokens) {
        return Msg.builder()
                .id(id)
                .role(MsgRole.USER)
                .addContent(new Content.TextContent(text))
                .tokenCount(tokens)
                .build();
    }

    private Msg verbatimMsg(String id, String text, int tokens) {
        return Msg.builder()
                .id(id)
                .role(MsgRole.USER)
                .addContent(new Content.TextContent(text))
                .tokenCount(tokens)
                .verbatimPreserved(true)
                .build();
    }

    @Test
    @DisplayName("addMessage should track token usage")
    void testAddMessageTracksTokens() {
        manager.addMessage(userMsg("m1", "hello", 100));
        manager.addMessage(userMsg("m2", "world", 200));

        assertEquals(300, manager.getTokenCount());
        assertEquals(2, manager.getMessages().size());
    }

    @Test
    @DisplayName("getMessages returns unmodifiable list")
    void testGetMessagesUnmodifiable() {
        manager.addMessage(userMsg("m1", "hello", 10));

        assertThrows(
                UnsupportedOperationException.class,
                () -> manager.getMessages().add(userMsg("m2", "x", 1)));
    }

    @Test
    @DisplayName("getTokenBudget returns correct snapshot")
    void testGetTokenBudget() {
        manager.addMessage(userMsg("m1", "hello", 1000));

        var budget = manager.getTokenBudget();
        assertEquals(200_000, budget.total());
        assertEquals(1000, budget.used());
        assertTrue(budget.remaining() > 0);
        assertTrue(budget.pressure() > 0.0f);
        assertTrue(budget.pressure() < 1.0f);
    }

    @Test
    @DisplayName("Verbatim messages are automatically tracked")
    void testVerbatimAutoTracked() {
        Msg v = verbatimMsg("v1", "important", 50);
        manager.addMessage(v);

        assertTrue(manager.getVerbatimMessageIds().contains("v1"));
    }

    @Test
    @DisplayName("markVerbatim adds to protected set")
    void testMarkVerbatim() {
        manager.addMessage(userMsg("m1", "hello", 10));
        manager.markVerbatim("m1");

        assertTrue(manager.getVerbatimMessageIds().contains("m1"));
    }

    @Test
    @DisplayName("compact should return empty when pressure below 80%")
    void testCompactBelowThreshold() {
        manager.addMessage(userMsg("m1", "hello", 10));

        StepVerifier.create(manager.compact()).verifyComplete();
    }

    @Test
    @DisplayName("compact should invoke pipeline when pressure is high")
    void testCompactWithHighPressure() {
        // Create a custom pipeline that always returns a result
        CompactionStrategy alwaysCompact =
                new CompactionStrategy() {
                    @Override
                    public boolean shouldTrigger(ContextState state) {
                        return true;
                    }

                    @Override
                    public Mono<CompactionResult> compact(
                            List<Msg> messages, CompactionConfig cfg) {
                        Msg summary =
                                Msg.builder()
                                        .id("summary")
                                        .role(MsgRole.ASSISTANT)
                                        .addContent(new Content.TextContent("summarized"))
                                        .tokenCount(10)
                                        .build();
                        BoundaryMarker marker =
                                new BoundaryMarker(
                                        Instant.now(), "test", messages.size(), 1, 50000);
                        return Mono.just(new CompactionResult(List.of(summary), 50000, marker));
                    }

                    @Override
                    public int priority() {
                        return 100;
                    }

                    @Override
                    public String name() {
                        return "test";
                    }
                };

        TokenBudgetManager budget = new TokenBudgetManager(100_000, 8000);
        CompactionPipeline pipeline = new CompactionPipeline(List.of(alwaysCompact));
        DefaultContextManager cm = new DefaultContextManager(budget, pipeline);

        // Add enough tokens to exceed 80% pressure
        Msg bigMsg = userMsg("big", "x".repeat(400), 80_000);
        cm.addMessage(bigMsg);

        float pressure = budget.pressure();
        assertTrue(pressure >= 0.80f, "Pressure should be >= 80% but was " + pressure);

        StepVerifier.create(cm.compact())
                .assertNext(
                        result -> {
                            assertEquals(50000, result.tokensSaved());
                            assertEquals(1, result.compactedMessages().size());
                        })
                .verifyComplete();

        // After compaction, messages should be replaced
        assertEquals(1, cm.getMessages().size());
        assertEquals("summary", cm.getMessages().get(0).id());

        // Boundary marker should be recorded
        assertEquals(1, cm.getBoundaryMarkerManager().compactionCount());
    }

    @Test
    @DisplayName("BoundaryMarkerManager tracks compaction history")
    void testBoundaryMarkerManager() {
        BoundaryMarkerManager bmm = manager.getBoundaryMarkerManager();
        assertEquals(0, bmm.compactionCount());
        assertEquals(0, bmm.totalTokensSaved());

        BoundaryMarker m1 = new BoundaryMarker(Instant.now(), "snip", 10, 8, 500);
        BoundaryMarker m2 = new BoundaryMarker(Instant.now(), "collapse", 8, 5, 300);
        bmm.record(m1);
        bmm.record(m2);

        assertEquals(2, bmm.compactionCount());
        assertEquals(800, bmm.totalTokensSaved());
        assertEquals(2, bmm.getMarkers().size());
        assertEquals("snip", bmm.getMarkers().get(0).strategyName());

        bmm.clear();
        assertEquals(0, bmm.compactionCount());
    }

    @Test
    @DisplayName("getVerbatimMessageIds returns unmodifiable set")
    void testVerbatimIdsUnmodifiable() {
        Set<String> ids = manager.getVerbatimMessageIds();
        assertThrows(UnsupportedOperationException.class, () -> ids.add("x"));
    }
}
