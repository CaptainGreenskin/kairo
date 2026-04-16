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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class HookStructuredReturnTest {

    /** A hook handler that returns a plain event (backward-compatible). */
    public static class PlainHandler {
        @PreActing
        public String onPreActing(String event) {
            return event + "-modified";
        }
    }

    /** A hook handler that returns a HookResult with ABORT. */
    public static class AbortHandler {
        @PreActing
        public HookResult<String> onPreActing(String event) {
            return HookResult.abort(event, "blocked by security policy");
        }
    }

    /** A hook handler that returns a HookResult with MODIFY. */
    public static class ModifyHandler {
        @PreActing
        public HookResult<String> onPreActing(String event) {
            return HookResult.modify(event, Map.of("command", "safe-command"));
        }
    }

    /** A hook handler that returns a HookResult with injected context. */
    public static class ContextHandler {
        @PreActing
        public HookResult<String> onPreActing(String event) {
            return HookResult.withContext(event, "Remember: follow coding standards");
        }
    }

    @Test
    void plainHandler_autoWrappedAsProceed() {
        DefaultHookChain chain = new DefaultHookChain();
        chain.register(new PlainHandler());

        StepVerifier.create(chain.firePreActingWithResult("input"))
                .assertNext(
                        result -> {
                            assertEquals(HookResult.Decision.CONTINUE, result.decision());
                            assertTrue(result.shouldProceed());
                            assertEquals("input-modified", result.event());
                        })
                .verifyComplete();
    }

    @Test
    void abortHandler_shortCircuitsChain() {
        DefaultHookChain chain = new DefaultHookChain();
        chain.register(new AbortHandler());

        StepVerifier.create(chain.firePreActingWithResult("input"))
                .assertNext(
                        result -> {
                            assertEquals(HookResult.Decision.ABORT, result.decision());
                            assertFalse(result.shouldProceed());
                            assertEquals("blocked by security policy", result.reason());
                        })
                .verifyComplete();
    }

    @Test
    void modifyHandler_returnsModifiedInput() {
        DefaultHookChain chain = new DefaultHookChain();
        chain.register(new ModifyHandler());

        StepVerifier.create(chain.firePreActingWithResult("input"))
                .assertNext(
                        result -> {
                            assertEquals(HookResult.Decision.MODIFY, result.decision());
                            assertTrue(result.shouldProceed());
                            assertTrue(result.hasModifiedInput());
                            assertEquals("safe-command", result.modifiedInput().get("command"));
                        })
                .verifyComplete();
    }

    @Test
    void contextHandler_returnsInjectedContext() {
        DefaultHookChain chain = new DefaultHookChain();
        chain.register(new ContextHandler());

        StepVerifier.create(chain.firePreActingWithResult("input"))
                .assertNext(
                        result -> {
                            assertTrue(result.shouldProceed());
                            assertTrue(result.hasInjectedContext());
                            assertEquals(
                                    "Remember: follow coding standards",
                                    result.injectedContext());
                        })
                .verifyComplete();
    }

    @Test
    void noHandlers_returnsProceed() {
        DefaultHookChain chain = new DefaultHookChain();

        StepVerifier.create(chain.firePreActingWithResult("input"))
                .assertNext(
                        result -> {
                            assertEquals(HookResult.Decision.CONTINUE, result.decision());
                            assertTrue(result.shouldProceed());
                            assertEquals("input", result.event());
                        })
                .verifyComplete();
    }

    @Test
    void abortHandler_preventsSubsequentHandlers() {
        DefaultHookChain chain = new DefaultHookChain();
        chain.register(new AbortHandler()); // This should abort
        chain.register(new PlainHandler()); // This should NOT run

        StepVerifier.create(chain.firePreActingWithResult("input"))
                .assertNext(
                        result -> {
                            assertFalse(result.shouldProceed());
                            // Event should NOT have "-modified" suffix since PlainHandler was skipped
                            assertEquals("input", result.event());
                        })
                .verifyComplete();
    }

    // ---- PreReasoning / PostReasoning WithResult handlers ----

    public static class PreReasoningAbortHandler {
        @PreReasoning
        public HookResult<PreReasoningEvent> onPre(PreReasoningEvent event) {
            return HookResult.abort(event, "reasoning blocked");
        }
    }

    public static class PreReasoningModifyHandler {
        @PreReasoning
        public HookResult<PreReasoningEvent> onPre(PreReasoningEvent event) {
            ModelConfig modified = ModelConfig.builder().model("modified-model").build();
            PreReasoningEvent newEvent = new PreReasoningEvent(event.messages(), modified, false);
            return HookResult.modify(newEvent, Map.of("model", "modified-model"));
        }
    }

    public static class PreReasoningContinueHandler {
        boolean called = false;

        @PreReasoning
        public HookResult<PreReasoningEvent> onPre(PreReasoningEvent event) {
            called = true;
            return HookResult.proceed(event);
        }
    }

    public static class PostReasoningAbortHandler {
        @PostReasoning
        public HookResult<PostReasoningEvent> onPost(PostReasoningEvent event) {
            return HookResult.abort(event, "post-reasoning blocked");
        }
    }

    public static class PostReasoningContinueHandler {
        boolean called = false;

        @PostReasoning
        public HookResult<PostReasoningEvent> onPost(PostReasoningEvent event) {
            called = true;
            return HookResult.proceed(event);
        }
    }

    public static class PreCompactAbortHandler {
        @PreCompact
        public HookResult<PreCompactEvent> onPre(PreCompactEvent event) {
            return HookResult.abort(event, "compaction blocked");
        }
    }

    public static class PostCompactAbortHandler {
        @PostCompact
        public HookResult<PostCompactEvent> onPost(PostCompactEvent event) {
            return HookResult.abort(event, "post-compact blocked");
        }
    }

    /** Void-returning hook (backward-compatible). */
    public static class VoidPreReasoningHandler {
        boolean called = false;

        @PreReasoning
        public void onPre(PreReasoningEvent event) {
            called = true;
        }
    }

    // ---- New tests ----

    @Test
    @DisplayName("firePreReasoningWithResult: CONTINUE proceeds normally")
    void firePreReasoningWithResult_CONTINUE() {
        DefaultHookChain chain = new DefaultHookChain();
        PreReasoningContinueHandler handler = new PreReasoningContinueHandler();
        chain.register(handler);

        ModelConfig config = ModelConfig.builder().model("test-model").build();
        PreReasoningEvent event = new PreReasoningEvent(List.of(), config, false);

        StepVerifier.create(chain.firePreReasoningWithResult(event))
                .assertNext(
                        result -> {
                            assertEquals(HookResult.Decision.CONTINUE, result.decision());
                            assertTrue(result.shouldProceed());
                            assertTrue(handler.called);
                            assertEquals("test-model", result.event().config().model());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("firePreReasoningWithResult: ABORT skips model call")
    void firePreReasoningWithResult_ABORT() {
        DefaultHookChain chain = new DefaultHookChain();
        chain.register(new PreReasoningAbortHandler());

        ModelConfig config = ModelConfig.builder().model("test-model").build();
        PreReasoningEvent event = new PreReasoningEvent(List.of(), config, false);

        StepVerifier.create(chain.firePreReasoningWithResult(event))
                .assertNext(
                        result -> {
                            assertEquals(HookResult.Decision.ABORT, result.decision());
                            assertFalse(result.shouldProceed());
                            assertEquals("reasoning blocked", result.reason());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("firePreReasoningWithResult: MODIFY carries modified config")
    void firePreReasoningWithResult_MODIFY() {
        DefaultHookChain chain = new DefaultHookChain();
        chain.register(new PreReasoningModifyHandler());

        ModelConfig config = ModelConfig.builder().model("original-model").build();
        PreReasoningEvent event = new PreReasoningEvent(List.of(), config, false);

        StepVerifier.create(chain.firePreReasoningWithResult(event))
                .assertNext(
                        result -> {
                            assertEquals(HookResult.Decision.MODIFY, result.decision());
                            assertTrue(result.shouldProceed());
                            assertTrue(result.hasModifiedInput());
                            assertEquals(
                                    "modified-model", result.event().config().model());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("firePostReasoningWithResult: ABORT prevents further processing")
    void firePostReasoningWithResult_ABORT() {
        DefaultHookChain chain = new DefaultHookChain();
        chain.register(new PostReasoningAbortHandler());

        ModelResponse response =
                new ModelResponse(
                        "resp-1",
                        List.of(),
                        new ModelResponse.Usage(100, 50, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "test-model");
        PostReasoningEvent event = new PostReasoningEvent(response, false);

        StepVerifier.create(chain.firePostReasoningWithResult(event))
                .assertNext(
                        result -> {
                            assertEquals(HookResult.Decision.ABORT, result.decision());
                            assertFalse(result.shouldProceed());
                            assertEquals("post-reasoning blocked", result.reason());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("firePostReasoningWithResult: CONTINUE proceeds to processModelResponse")
    void firePostReasoningWithResult_CONTINUE() {
        DefaultHookChain chain = new DefaultHookChain();
        PostReasoningContinueHandler handler = new PostReasoningContinueHandler();
        chain.register(handler);

        ModelResponse response =
                new ModelResponse(
                        "resp-1",
                        List.of(),
                        new ModelResponse.Usage(100, 50, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "test-model");
        PostReasoningEvent event = new PostReasoningEvent(response, false);

        StepVerifier.create(chain.firePostReasoningWithResult(event))
                .assertNext(
                        result -> {
                            assertEquals(HookResult.Decision.CONTINUE, result.decision());
                            assertTrue(result.shouldProceed());
                            assertTrue(handler.called);
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("firePreCompactWithResult: ABORT skips compaction")
    void firePreCompactWithResult_ABORT() {
        DefaultHookChain chain = new DefaultHookChain();
        chain.register(new PreCompactAbortHandler());

        PreCompactEvent event = new PreCompactEvent(List.of(), 0.85);

        StepVerifier.create(chain.firePreCompactWithResult(event))
                .assertNext(
                        result -> {
                            assertEquals(HookResult.Decision.ABORT, result.decision());
                            assertFalse(result.shouldProceed());
                            assertEquals("compaction blocked", result.reason());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("firePostCompactWithResult: ABORT keeps original result")
    void firePostCompactWithResult_ABORT() {
        DefaultHookChain chain = new DefaultHookChain();
        chain.register(new PostCompactAbortHandler());

        PostCompactEvent event = new PostCompactEvent(List.of(), 500, "sliding-window", List.of());

        StepVerifier.create(chain.firePostCompactWithResult(event))
                .assertNext(
                        result -> {
                            assertEquals(HookResult.Decision.ABORT, result.decision());
                            assertFalse(result.shouldProceed());
                            assertEquals("post-compact blocked", result.reason());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Backward compat: void-returning hook works with WithResult methods")
    void backwardCompat_voidHook() {
        DefaultHookChain chain = new DefaultHookChain();
        VoidPreReasoningHandler handler = new VoidPreReasoningHandler();
        chain.register(handler);

        ModelConfig config = ModelConfig.builder().model("test").build();
        PreReasoningEvent event = new PreReasoningEvent(List.of(), config, false);

        StepVerifier.create(chain.firePreReasoningWithResult(event))
                .assertNext(
                        result -> {
                            // void handler returns null, so event passes through as CONTINUE
                            assertEquals(HookResult.Decision.CONTINUE, result.decision());
                            assertTrue(result.shouldProceed());
                            assertTrue(handler.called);
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("HookChain default methods return HookResult.proceed()")
    void defaultMethods_returnProceed() {
        // Use an anonymous HookChain that only implements the required abstract methods
        // The default WithResult methods should delegate and wrap as proceed()
        HookChain minimalChain =
                new HookChain() {
                    @Override
                    public void register(Object hookHandler) {}

                    @Override
                    public void unregister(Object hookHandler) {}

                    @Override
                    public <T> reactor.core.publisher.Mono<T> firePreReasoning(T event) {
                        return reactor.core.publisher.Mono.just(event);
                    }

                    @Override
                    public <T> reactor.core.publisher.Mono<T> firePostReasoning(T event) {
                        return reactor.core.publisher.Mono.just(event);
                    }

                    @Override
                    public <T> reactor.core.publisher.Mono<T> firePreActing(T event) {
                        return reactor.core.publisher.Mono.just(event);
                    }

                    @Override
                    public <T> reactor.core.publisher.Mono<T> firePostActing(T event) {
                        return reactor.core.publisher.Mono.just(event);
                    }

                    @Override
                    public <T> reactor.core.publisher.Mono<T> firePreCompact(T event) {
                        return reactor.core.publisher.Mono.just(event);
                    }

                    @Override
                    public <T> reactor.core.publisher.Mono<T> firePostCompact(T event) {
                        return reactor.core.publisher.Mono.just(event);
                    }
                };

        // All default WithResult methods should return CONTINUE
        StepVerifier.create(minimalChain.firePreReasoningWithResult("test"))
                .assertNext(
                        r -> {
                            assertEquals(HookResult.Decision.CONTINUE, r.decision());
                            assertEquals("test", r.event());
                        })
                .verifyComplete();

        StepVerifier.create(minimalChain.firePostReasoningWithResult("test"))
                .assertNext(r -> assertEquals(HookResult.Decision.CONTINUE, r.decision()))
                .verifyComplete();

        StepVerifier.create(minimalChain.firePreCompactWithResult("test"))
                .assertNext(r -> assertEquals(HookResult.Decision.CONTINUE, r.decision()))
                .verifyComplete();

        StepVerifier.create(minimalChain.firePostCompactWithResult("test"))
                .assertNext(r -> assertEquals(HookResult.Decision.CONTINUE, r.decision()))
                .verifyComplete();
    }

    @Test
    @DisplayName("HookResult factory methods produce correct decisions")
    void hookResult_factoryMethods() {
        HookResult<String> proceed = HookResult.proceed("e");
        assertEquals(HookResult.Decision.CONTINUE, proceed.decision());
        assertTrue(proceed.shouldProceed());
        assertNull(proceed.reason());
        assertFalse(proceed.hasModifiedInput());
        assertFalse(proceed.hasInjectedContext());

        HookResult<String> abort = HookResult.abort("e", "blocked");
        assertEquals(HookResult.Decision.ABORT, abort.decision());
        assertFalse(abort.shouldProceed());
        assertEquals("blocked", abort.reason());

        HookResult<String> modify = HookResult.modify("e", Map.of("k", "v"));
        assertEquals(HookResult.Decision.MODIFY, modify.decision());
        assertTrue(modify.shouldProceed());
        assertTrue(modify.hasModifiedInput());
        assertEquals("v", modify.modifiedInput().get("k"));

        HookResult<String> ctx = HookResult.withContext("e", "extra");
        assertEquals(HookResult.Decision.CONTINUE, ctx.decision());
        assertTrue(ctx.hasInjectedContext());
        assertEquals("extra", ctx.injectedContext());
    }
}
