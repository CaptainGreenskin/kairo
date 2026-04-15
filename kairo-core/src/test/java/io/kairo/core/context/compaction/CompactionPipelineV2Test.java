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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.kairo.api.context.*;
import io.kairo.api.hook.HookChain;
import io.kairo.api.hook.PostCompactEvent;
import io.kairo.api.hook.PreCompactEvent;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CompactionPipelineV2Test {

    private CompactionConfig config;

    @BeforeEach
    void setUp() {
        config = new CompactionConfig(100_000, true, null);
    }

    private List<Msg> sampleMessages(int count) {
        List<Msg> msgs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            msgs.add(
                    Msg.builder()
                            .id("msg-" + i)
                            .role(MsgRole.USER)
                            .addContent(new Content.TextContent("Message " + i))
                            .tokenCount(100)
                            .build());
        }
        return msgs;
    }

    private CompactionStrategy stubStrategy(
            String name, int priority, float triggerThreshold, int tokensSaved) {
        return new CompactionStrategy() {
            @Override
            public boolean shouldTrigger(ContextState state) {
                return state.pressure() >= triggerThreshold;
            }

            @Override
            public Mono<CompactionResult> compact(List<Msg> messages, CompactionConfig cfg) {
                BoundaryMarker marker =
                        new BoundaryMarker(
                                Instant.now(), name, messages.size(), messages.size(), tokensSaved);
                return Mono.just(new CompactionResult(messages, tokensSaved, marker));
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    @Test
    @DisplayName("Hybrid thresholds: high pressure triggers stages")
    void testHybridThresholdsTrigger() {
        CompactionStrategy stage = stubStrategy("snip", 100, 0.80f, 500);
        // Create pipeline with modelId to enable context window resolution
        CompactionPipeline pipeline =
                new CompactionPipeline(List.of(stage), "claude-sonnet-4-20250514", null);

        StepVerifier.create(pipeline.execute(sampleMessages(5), Set.of(), 0.90f, config))
                .assertNext(result -> assertEquals(500, result.tokensSaved()))
                .verifyComplete();
    }

    @Test
    @DisplayName("PreCompact hook fires before compaction")
    void testPreCompactHookFires() {
        HookChain hookChain = mock(HookChain.class);

        // The hook fires and returns the event unmodified
        when(hookChain.firePreCompact(any(PreCompactEvent.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(hookChain.firePostCompact(any(PostCompactEvent.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        CompactionStrategy stage = stubStrategy("test", 100, 0.0f, 200);
        CompactionPipeline pipeline = new CompactionPipeline(List.of(stage), null, hookChain);

        StepVerifier.create(pipeline.execute(sampleMessages(3), Set.of(), 0.90f, config))
                .assertNext(
                        result -> {
                            assertEquals(200, result.tokensSaved());
                        })
                .verifyComplete();

        verify(hookChain).firePreCompact(any(PreCompactEvent.class));
    }

    @Test
    @DisplayName("PostCompact hook fires after compaction with correct tokensSaved")
    void testPostCompactHookFires() {
        HookChain hookChain = mock(HookChain.class);

        when(hookChain.firePreCompact(any(PreCompactEvent.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(hookChain.firePostCompact(any(PostCompactEvent.class)))
                .thenAnswer(
                        inv -> {
                            PostCompactEvent event = inv.getArgument(0);
                            assertEquals(200, event.tokensSaved());
                            return Mono.just(event);
                        });

        CompactionStrategy stage = stubStrategy("test", 100, 0.0f, 200);
        CompactionPipeline pipeline = new CompactionPipeline(List.of(stage), null, hookChain);

        StepVerifier.create(pipeline.execute(sampleMessages(3), Set.of(), 0.90f, config))
                .assertNext(result -> assertEquals(200, result.tokensSaved()))
                .verifyComplete();

        verify(hookChain).firePostCompact(any(PostCompactEvent.class));
    }

    @Test
    @DisplayName("PreCompact cancellation prevents compaction")
    void testPreCompactCancellationPreventsCompaction() {
        HookChain hookChain = mock(HookChain.class);

        when(hookChain.firePreCompact(any(PreCompactEvent.class)))
                .thenAnswer(
                        inv -> {
                            PreCompactEvent event = inv.getArgument(0);
                            event.cancel();
                            return Mono.just(event);
                        });

        CompactionStrategy stage = stubStrategy("test", 100, 0.0f, 200);
        CompactionPipeline pipeline = new CompactionPipeline(List.of(stage), null, hookChain);

        // Should return empty — compaction was cancelled
        StepVerifier.create(pipeline.execute(sampleMessages(3), Set.of(), 0.90f, config))
                .verifyComplete();

        // PostCompact should NOT have fired
        verify(hookChain, never()).firePostCompact(any(PostCompactEvent.class));
    }

    @Test
    @DisplayName("PostCompact recovery messages are merged into result")
    void testPostCompactRecoveryMessagesMerged() {
        HookChain hookChain = mock(HookChain.class);

        when(hookChain.firePreCompact(any(PreCompactEvent.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(hookChain.firePostCompact(any(PostCompactEvent.class)))
                .thenAnswer(
                        inv -> {
                            PostCompactEvent event = inv.getArgument(0);
                            // Add a recovery message
                            Msg recovery =
                                    Msg.builder()
                                            .role(MsgRole.USER)
                                            .addContent(
                                                    new Content.TextContent(
                                                            "[Recovery] file re-read"))
                                            .metadata("recovery", true)
                                            .build();
                            event.addRecoveryMessage(recovery);
                            return Mono.just(event);
                        });

        CompactionStrategy stage = stubStrategy("test", 100, 0.0f, 200);
        CompactionPipeline pipeline = new CompactionPipeline(List.of(stage), null, hookChain);

        List<Msg> input = sampleMessages(3);
        StepVerifier.create(pipeline.execute(input, Set.of(), 0.90f, config))
                .assertNext(
                        result -> {
                            // Original 3 messages + 1 recovery message
                            assertEquals(input.size() + 1, result.compactedMessages().size());
                            Msg lastMsg =
                                    result.compactedMessages()
                                            .get(result.compactedMessages().size() - 1);
                            assertTrue(lastMsg.text().contains("[Recovery]"));
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("ThinkingContent in old messages: pipeline compresses them via strategy")
    void testThinkingContentHandling() {
        // Create messages with ThinkingContent
        List<Msg> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Msg.Builder builder =
                    Msg.builder()
                            .id("msg-" + i)
                            .role(MsgRole.ASSISTANT)
                            .addContent(new Content.TextContent("Response " + i))
                            .addContent(new Content.ThinkingContent("thinking about " + i, 1000))
                            .tokenCount(500);
            messages.add(builder.build());
        }

        // Strategy that strips ThinkingContent from old messages (first 2) but keeps recent (last
        // 3)
        CompactionStrategy thinkingStripper =
                new CompactionStrategy() {
                    @Override
                    public boolean shouldTrigger(ContextState state) {
                        return true;
                    }

                    @Override
                    public Mono<CompactionResult> compact(List<Msg> msgs, CompactionConfig cfg) {
                        List<Msg> result = new ArrayList<>();
                        int preserveFrom = Math.max(0, msgs.size() - 3);
                        for (int i = 0; i < msgs.size(); i++) {
                            if (i < preserveFrom) {
                                // Strip thinking from old messages
                                List<Content> filtered =
                                        msgs.get(i).contents().stream()
                                                .filter(
                                                        c ->
                                                                !(c
                                                                        instanceof
                                                                        Content.ThinkingContent))
                                                .toList();
                                result.add(
                                        Msg.builder()
                                                .id(msgs.get(i).id())
                                                .role(msgs.get(i).role())
                                                .contents(filtered)
                                                .tokenCount(100)
                                                .build());
                            } else {
                                result.add(msgs.get(i));
                            }
                        }
                        BoundaryMarker marker =
                                new BoundaryMarker(
                                        Instant.now(),
                                        "thinking-strip",
                                        msgs.size(),
                                        result.size(),
                                        800);
                        return Mono.just(new CompactionResult(result, 800, marker));
                    }

                    @Override
                    public int priority() {
                        return 100;
                    }

                    @Override
                    public String name() {
                        return "thinking-strip";
                    }
                };

        CompactionPipeline pipeline = new CompactionPipeline(List.of(thinkingStripper));

        StepVerifier.create(pipeline.execute(messages, Set.of(), 0.90f, config))
                .assertNext(
                        result -> {
                            // Old messages (0, 1) should have no ThinkingContent
                            for (int i = 0; i < 2; i++) {
                                Msg msg = result.compactedMessages().get(i);
                                assertTrue(
                                        msg.contents().stream()
                                                .noneMatch(
                                                        c -> c instanceof Content.ThinkingContent));
                            }
                            // Recent messages (2, 3, 4) should still have ThinkingContent
                            for (int i = 2; i < 5; i++) {
                                Msg msg = result.compactedMessages().get(i);
                                assertTrue(
                                        msg.contents().stream()
                                                .anyMatch(
                                                        c -> c instanceof Content.ThinkingContent));
                            }
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Pipeline with modelId resolves context window in ContextState")
    void testModelIdContextWindowResolution() {
        List<Boolean> triggered = new ArrayList<>();

        CompactionStrategy inspecting =
                new CompactionStrategy() {
                    @Override
                    public boolean shouldTrigger(ContextState state) {
                        // Pipeline creates ContextState with contextWindow from ModelRegistry
                        triggered.add(state.contextWindow() > 0);
                        return true;
                    }

                    @Override
                    public Mono<CompactionResult> compact(
                            List<Msg> messages, CompactionConfig cfg) {
                        BoundaryMarker m = new BoundaryMarker(Instant.now(), "inspect", 1, 1, 0);
                        return Mono.just(new CompactionResult(messages, 0, m));
                    }

                    @Override
                    public int priority() {
                        return 100;
                    }

                    @Override
                    public String name() {
                        return "inspect";
                    }
                };

        CompactionPipeline pipeline =
                new CompactionPipeline(List.of(inspecting), "claude-sonnet-4-20250514", null);

        StepVerifier.create(pipeline.execute(sampleMessages(1), Set.of(), 0.90f, config))
                .assertNext(result -> assertTrue(triggered.get(0)))
                .verifyComplete();
    }
}
