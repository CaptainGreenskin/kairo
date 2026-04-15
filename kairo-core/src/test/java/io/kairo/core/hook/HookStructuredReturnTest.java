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
import java.util.Map;
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
}
