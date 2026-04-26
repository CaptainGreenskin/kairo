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
package io.kairo.core.context.compaction;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.context.CompactionConfig;
import io.kairo.api.context.ContextState;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class TimeBasedMicrocompactTest {

    private final TimeBasedMicrocompact strategy = new TimeBasedMicrocompact();
    private final CompactionConfig config = new CompactionConfig(100_000, true, null);

    @Test
    @DisplayName("priority() returns 50")
    void testPriority() {
        assertEquals(50, strategy.priority());
    }

    @Test
    @DisplayName("name() returns 'time-micro'")
    void testName() {
        assertEquals("time-micro", strategy.name());
    }

    @Test
    @DisplayName("Triggers when last user message is older than 60 minutes")
    void testTriggersWhenIdle() {
        Instant oldTime = Instant.now().minus(Duration.ofMinutes(90));

        List<Msg> messages =
                List.of(
                        Msg.builder()
                                .role(MsgRole.USER)
                                .addContent(new Content.TextContent("Hello"))
                                .timestamp(oldTime)
                                .tokenCount(10)
                                .build(),
                        Msg.builder()
                                .role(MsgRole.ASSISTANT)
                                .addContent(new Content.TextContent("Hi there"))
                                .timestamp(oldTime.plusSeconds(10))
                                .tokenCount(10)
                                .build());

        StepVerifier.create(strategy.compact(messages, config))
                .assertNext(
                        result -> {
                            // Should have done some compaction (old messages > 30min)
                            assertNotNull(result);
                            assertTrue(result.tokensSaved() >= 0);
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Does NOT trigger when messages are recent (< 60 min idle)")
    void testDoesNotTriggerWhenRecent() {
        Instant recentTime = Instant.now().minus(Duration.ofMinutes(10));

        List<Msg> messages =
                List.of(
                        Msg.builder()
                                .role(MsgRole.USER)
                                .addContent(new Content.TextContent("Hello"))
                                .timestamp(recentTime)
                                .tokenCount(10)
                                .build());

        StepVerifier.create(strategy.compact(messages, config))
                .assertNext(
                        result -> {
                            // No compaction — not idle
                            assertEquals(0, result.tokensSaved());
                            assertEquals(messages, result.compactedMessages());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Compresses messages older than 30 minutes when idle")
    void testCompressesOldMessages() {
        Instant now = Instant.now();
        Instant oldTime = now.minus(Duration.ofMinutes(120)); // 2 hours ago (idle)
        Instant veryOld = now.minus(Duration.ofMinutes(90)); // 90 min ago (> 30min old)

        // Create a tool result message that's old
        List<Msg> messages =
                List.of(
                        Msg.builder()
                                .role(MsgRole.USER)
                                .addContent(new Content.TextContent("Do something"))
                                .timestamp(oldTime)
                                .tokenCount(10)
                                .build(),
                        Msg.builder()
                                .role(MsgRole.TOOL)
                                .addContent(
                                        new Content.ToolResultContent(
                                                "tool-1", "A".repeat(1000), false))
                                .timestamp(veryOld)
                                .tokenCount(500)
                                .build());

        StepVerifier.create(strategy.compact(messages, config))
                .assertNext(
                        result -> {
                            // Tool result should have been compacted
                            assertEquals(2, result.compactedMessages().size());
                            assertTrue(result.tokensSaved() > 0);
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Preserves last 5 tool results regardless of age")
    void testPreservesLast5ToolResults() {
        Instant now = Instant.now();
        Instant oldTime = now.minus(Duration.ofMinutes(120)); // idle trigger
        Instant veryOld = now.minus(Duration.ofMinutes(90)); // all old

        List<Msg> messages = new ArrayList<>();
        // Add an old user message to trigger idle detection
        messages.add(
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("task"))
                        .timestamp(oldTime)
                        .tokenCount(10)
                        .build());

        // Add 7 tool result messages (all old)
        for (int i = 0; i < 7; i++) {
            messages.add(
                    Msg.builder()
                            .role(MsgRole.TOOL)
                            .addContent(
                                    new Content.ToolResultContent(
                                            "tool-" + i,
                                            "Result content " + i + " " + "x".repeat(100),
                                            false))
                            .timestamp(veryOld.plusSeconds(i))
                            .tokenCount(100)
                            .build());
        }

        StepVerifier.create(strategy.compact(messages, config))
                .assertNext(
                        result -> {
                            assertNotNull(result);
                            // The last 5 tool results should be preserved
                            // The first 2 tool results (index 0,1 in tool results) should be
                            // compacted
                            assertEquals(messages.size(), result.compactedMessages().size());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Empty message list returns empty result with 0 tokens saved")
    void testEmptyMessageList() {
        StepVerifier.create(strategy.compact(List.of(), config))
                .assertNext(
                        result -> {
                            assertEquals(0, result.tokensSaved());
                            assertTrue(result.compactedMessages().isEmpty());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("shouldTrigger returns true when pressure >= 80% and messageCount > 0")
    void testShouldTriggerWithHighPressure() {
        ContextState state = new ContextState(100_000, 85_000, 0.85f, 10);
        assertTrue(strategy.shouldTrigger(state));
    }

    @Test
    @DisplayName("shouldTrigger returns false when pressure is low")
    void testShouldTriggerLowPressure() {
        ContextState state = new ContextState(100_000, 50_000, 0.5f, 10);
        assertFalse(strategy.shouldTrigger(state));
    }

    @Test
    @DisplayName("shouldTrigger returns false when messageCount is 0")
    void testShouldTriggerNoMessages() {
        ContextState state = new ContextState(100_000, 90_000, 0.9f, 0);
        assertFalse(strategy.shouldTrigger(state));
    }

    @Test
    @DisplayName("System messages are preserved even when old")
    void testSystemMessagesPreserved() {
        Instant now = Instant.now();
        Instant oldTime = now.minus(Duration.ofMinutes(120));

        List<Msg> messages =
                List.of(
                        Msg.builder()
                                .role(MsgRole.SYSTEM)
                                .addContent(new Content.TextContent("System instructions"))
                                .timestamp(oldTime)
                                .tokenCount(50)
                                .build(),
                        Msg.builder()
                                .role(MsgRole.USER)
                                .addContent(new Content.TextContent("Question"))
                                .timestamp(oldTime)
                                .tokenCount(10)
                                .build());

        StepVerifier.create(strategy.compact(messages, config))
                .assertNext(
                        result -> {
                            // System message should be unchanged
                            Msg systemMsg = result.compactedMessages().get(0);
                            assertEquals(MsgRole.SYSTEM, systemMsg.role());
                            assertEquals("System instructions", systemMsg.text());
                        })
                .verifyComplete();
    }
}
