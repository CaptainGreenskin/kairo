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
package io.kairo.core.hook;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.hook.*;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.ToolResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class DefaultHookChainTest {

    private DefaultHookChain chain;

    @BeforeEach
    void setUp() {
        chain = new DefaultHookChain();
    }

    // ---- Test handler classes ----

    static class PreReasoningHandler {
        final List<String> calls = Collections.synchronizedList(new ArrayList<>());

        @PreReasoning(order = 10)
        public PreReasoningEvent onPreReasoning(PreReasoningEvent event) {
            calls.add("pre-reasoning");
            return event;
        }
    }

    static class PostReasoningHandler {
        final List<String> calls = Collections.synchronizedList(new ArrayList<>());

        @PostReasoning(order = 10)
        public PostReasoningEvent onPostReasoning(PostReasoningEvent event) {
            calls.add("post-reasoning");
            return event;
        }
    }

    static class PreActingHandler {
        final List<String> calls = Collections.synchronizedList(new ArrayList<>());

        @PreActing(order = 10)
        public PreActingEvent onPreActing(PreActingEvent event) {
            calls.add("pre-acting");
            return event;
        }
    }

    static class PostActingHandler {
        final List<String> calls = Collections.synchronizedList(new ArrayList<>());

        @PostActing(order = 10)
        public PostActingEvent onPostActing(PostActingEvent event) {
            calls.add("post-acting");
            return event;
        }
    }

    static class OrderedHandler {
        final List<String> calls = Collections.synchronizedList(new ArrayList<>());

        @PreReasoning(order = 20)
        public PreReasoningEvent second(PreReasoningEvent event) {
            calls.add("second");
            return event;
        }

        @PreReasoning(order = 5)
        public PreReasoningEvent first(PreReasoningEvent event) {
            calls.add("first");
            return event;
        }
    }

    static class ThrowingHandler {
        @PreReasoning(order = 10)
        public PreReasoningEvent boom(PreReasoningEvent event) {
            throw new IllegalStateException("hook explosion");
        }
    }

    static class CancellingHandler {
        @PreReasoning(order = 5)
        public PreReasoningEvent cancel(PreReasoningEvent event) {
            return new PreReasoningEvent(event.messages(), event.config(), true);
        }
    }

    static class AfterCancelHandler {
        boolean called = false;

        @PreReasoning(order = 10)
        public PreReasoningEvent after(PreReasoningEvent event) {
            called = true;
            return event;
        }
    }

    // ---- Tests ----

    @Test
    @DisplayName("PreReasoning hook is called before model invocation")
    void testPreReasoningHookCalled() {
        PreReasoningHandler handler = new PreReasoningHandler();
        chain.register(handler);

        ModelConfig config = ModelConfig.builder().model("test-model").build();
        PreReasoningEvent event = new PreReasoningEvent(List.of(), config, false);

        StepVerifier.create(chain.firePreReasoning(event))
                .assertNext(
                        e -> {
                            assertEquals(1, handler.calls.size());
                            assertEquals("pre-reasoning", handler.calls.get(0));
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("PostReasoning hook receives model response")
    void testPostReasoningHookCalled() {
        PostReasoningHandler handler = new PostReasoningHandler();
        chain.register(handler);

        ModelResponse response =
                new ModelResponse(
                        "resp-1",
                        List.of(),
                        new ModelResponse.Usage(100, 50, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "test-model");
        PostReasoningEvent event = new PostReasoningEvent(response, false);

        StepVerifier.create(chain.firePostReasoning(event))
                .assertNext(e -> assertEquals(1, handler.calls.size()))
                .verifyComplete();
    }

    @Test
    @DisplayName("PreActing hook is called before tool execution")
    void testPreActingHookCalled() {
        PreActingHandler handler = new PreActingHandler();
        chain.register(handler);

        PreActingEvent event = new PreActingEvent("read_file", Map.of("path", "/test"), false);

        StepVerifier.create(chain.firePreActing(event))
                .assertNext(e -> assertEquals("pre-acting", handler.calls.get(0)))
                .verifyComplete();
    }

    @Test
    @DisplayName("PostActing hook receives tool result")
    void testPostActingHookCalled() {
        PostActingHandler handler = new PostActingHandler();
        chain.register(handler);

        ToolResult result = new ToolResult("tu-1", "file contents", false, Map.of());
        PostActingEvent event = new PostActingEvent("read_file", result);

        StepVerifier.create(chain.firePostActing(event))
                .assertNext(e -> assertEquals("post-acting", handler.calls.get(0)))
                .verifyComplete();
    }

    @Test
    @DisplayName("Hooks execute in order (by annotation order value)")
    void testHookOrdering() {
        OrderedHandler handler = new OrderedHandler();
        chain.register(handler);

        ModelConfig config = ModelConfig.builder().model("test").build();
        PreReasoningEvent event = new PreReasoningEvent(List.of(), config, false);

        StepVerifier.create(chain.firePreReasoning(event))
                .assertNext(
                        e -> {
                            assertEquals(2, handler.calls.size());
                            assertEquals("first", handler.calls.get(0));
                            assertEquals("second", handler.calls.get(1));
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Exception in hook propagates as Mono error")
    void testExceptionInHookPropagatesError() {
        chain.register(new ThrowingHandler());

        ModelConfig config = ModelConfig.builder().model("test").build();
        PreReasoningEvent event = new PreReasoningEvent(List.of(), config, false);

        StepVerifier.create(chain.firePreReasoning(event))
                .expectErrorMatches(
                        e ->
                                e instanceof IllegalStateException
                                        && e.getMessage().contains("hook explosion"))
                .verify();
    }

    @Test
    @DisplayName("Cancelled event short-circuits the chain")
    void testCancelledEventShortCircuits() {
        CancellingHandler canceller = new CancellingHandler();
        AfterCancelHandler afterCancel = new AfterCancelHandler();
        chain.register(canceller);
        chain.register(afterCancel);

        ModelConfig config = ModelConfig.builder().model("test").build();
        PreReasoningEvent event = new PreReasoningEvent(List.of(), config, false);

        StepVerifier.create(chain.firePreReasoning(event))
                .assertNext(
                        e -> {
                            assertTrue(e.cancelled());
                            assertFalse(afterCancel.called);
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Empty hook chain passes event through unchanged")
    void testEmptyHookChain() {
        ModelConfig config = ModelConfig.builder().model("test").build();
        PreReasoningEvent event = new PreReasoningEvent(List.of(), config, false);

        StepVerifier.create(chain.firePreReasoning(event))
                .assertNext(e -> assertFalse(e.cancelled()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Multiple handlers of same type all execute")
    void testMultipleHandlersSameType() {
        PreReasoningHandler h1 = new PreReasoningHandler();
        PreReasoningHandler h2 = new PreReasoningHandler();
        chain.register(h1);
        chain.register(h2);

        ModelConfig config = ModelConfig.builder().model("test").build();
        PreReasoningEvent event = new PreReasoningEvent(List.of(), config, false);

        StepVerifier.create(chain.firePreReasoning(event))
                .assertNext(
                        e -> {
                            assertEquals(1, h1.calls.size());
                            assertEquals(1, h2.calls.size());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Unregister removes handler from chain")
    void testUnregister() {
        PreReasoningHandler handler = new PreReasoningHandler();
        chain.register(handler);
        chain.unregister(handler);

        ModelConfig config = ModelConfig.builder().model("test").build();
        PreReasoningEvent event = new PreReasoningEvent(List.of(), config, false);

        StepVerifier.create(chain.firePreReasoning(event))
                .assertNext(e -> assertTrue(handler.calls.isEmpty()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Registering null handler is ignored")
    void testRegisterNullIgnored() {
        chain.register(null);
        // Should not throw, chain just stays empty
        ModelConfig config = ModelConfig.builder().model("test").build();
        PreReasoningEvent event = new PreReasoningEvent(List.of(), config, false);

        StepVerifier.create(chain.firePreReasoning(event)).expectNext(event).verifyComplete();
    }

    @Test
    @DisplayName("Event without cancelled() method doesn't short-circuit")
    void testEventWithoutCancelledMethod() {
        // Use a simple string as the event — it has no cancelled() method
        chain.register(
                new Object() {
                    @PreReasoning
                    public String handle(String s) {
                        return s + "-modified";
                    }
                });

        StepVerifier.create(chain.firePreReasoning("input"))
                .assertNext(result -> assertEquals("input-modified", result))
                .verifyComplete();
    }
}
