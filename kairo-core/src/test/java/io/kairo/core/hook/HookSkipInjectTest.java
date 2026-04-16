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

import io.kairo.api.hook.HookResult;
import io.kairo.api.hook.PreActing;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class HookSkipInjectTest {

    /** A hook handler that returns SKIP. */
    public static class SkipHandler {
        boolean called = false;

        @PreActing
        public HookResult<String> onPreActing(String event) {
            called = true;
            return HookResult.skip(event, "skip reason");
        }
    }

    /** A hook handler that returns CONTINUE. */
    public static class ContinueHandler {
        boolean called = false;

        @PreActing
        public HookResult<String> onPreActing(String event) {
            called = true;
            return HookResult.proceed(event);
        }
    }

    /** A hook handler that returns ABORT. */
    public static class AbortHandler {
        @PreActing
        public HookResult<String> onPreActing(String event) {
            return HookResult.abort(event, "abort reason");
        }
    }

    /** A hook handler that returns INJECT with a specific message. */
    public static class InjectHandler1 {
        @PreActing
        public HookResult<String> onPreActing(String event) {
            Msg msg =
                    Msg.builder()
                            .role(MsgRole.ASSISTANT)
                            .addContent(new Content.TextContent("injected-1"))
                            .build();
            return HookResult.inject(event, msg, "hook-1");
        }
    }

    /** A second INJECT handler with a different message. */
    public static class InjectHandler2 {
        @PreActing
        public HookResult<String> onPreActing(String event) {
            Msg msg =
                    Msg.builder()
                            .role(MsgRole.ASSISTANT)
                            .addContent(new Content.TextContent("injected-2"))
                            .build();
            return HookResult.inject(event, msg, "hook-2");
        }
    }

    @Test
    @DisplayName("SKIP does not short-circuit chain — both hooks execute, higher priority wins")
    void skip_doesNotShortCircuitChain() {
        DefaultHookChain chain = new DefaultHookChain();
        SkipHandler skipHandler = new SkipHandler();
        ContinueHandler continueHandler = new ContinueHandler();
        chain.register(skipHandler);
        chain.register(continueHandler);

        StepVerifier.create(chain.firePreActingWithResult("input"))
                .assertNext(
                        result -> {
                            // Both handlers should have been called
                            assertTrue(skipHandler.called, "SkipHandler should have been called");
                            assertTrue(
                                    continueHandler.called,
                                    "ContinueHandler should have been called");
                            // SKIP (priority 3) wins over CONTINUE (priority 0)
                            assertEquals(HookResult.Decision.SKIP, result.decision());
                            assertTrue(result.shouldSkip());
                            assertEquals("skip reason", result.reason());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Multiple INJECT hooks — first injected message is in final result")
    void inject_multipleHooksAppendInOrder() {
        DefaultHookChain chain = new DefaultHookChain();
        chain.register(new InjectHandler1());
        chain.register(new InjectHandler2());

        StepVerifier.create(chain.firePreActingWithResult("input"))
                .assertNext(
                        result -> {
                            assertEquals(HookResult.Decision.INJECT, result.decision());
                            assertTrue(result.hasInjectedMessage());
                            // The first injected message should be from hook-1
                            assertEquals("injected-1", result.injectedMessage().text());
                            // The last hook source should be hook-2
                            assertEquals("hook-2", result.hookSource());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Priority merge: ABORT wins over SKIP")
    void priorityMerge_abortWinsOverSkip() {
        DefaultHookChain chain = new DefaultHookChain();
        chain.register(new SkipHandler());
        chain.register(new AbortHandler());

        StepVerifier.create(chain.firePreActingWithResult("input"))
                .assertNext(
                        result -> {
                            // ABORT short-circuits and wins
                            assertEquals(HookResult.Decision.ABORT, result.decision());
                            assertFalse(result.shouldProceed());
                            assertEquals("abort reason", result.reason());
                        })
                .verifyComplete();
    }

    @Test
    @DisplayName("Priority merge: SKIP wins over CONTINUE")
    void priorityMerge_skipWinsOverContinue() {
        DefaultHookChain chain = new DefaultHookChain();
        // Register CONTINUE first, then SKIP
        chain.register(new ContinueHandler());
        chain.register(new SkipHandler());

        StepVerifier.create(chain.firePreActingWithResult("input"))
                .assertNext(
                        result -> {
                            assertEquals(HookResult.Decision.SKIP, result.decision());
                            assertTrue(result.shouldSkip());
                            assertEquals("skip reason", result.reason());
                        })
                .verifyComplete();
    }
}
