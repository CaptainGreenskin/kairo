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
package io.kairo.core.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.agent.AgentState;
import io.kairo.api.hook.*;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.tool.ToolResult;
import io.kairo.core.hook.DefaultHookChain;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Integration tests for HookChain covering all hook lifecycle events and decision merge behavior.
 *
 * <p>Tests verify: single hook decisions (CONTINUE, SKIP, ABORT, INJECT, MODIFY), multi-hook
 * priority merging, exception isolation, context propagation, and session lifecycle hooks.
 */
@Tag("integration")
class HookChainIntegrationIT {

    private DefaultHookChain chain;

    @BeforeEach
    void setUp() {
        chain = new DefaultHookChain();
    }

    // ================================
    //  Stub Hook Handlers
    // ================================

    /** A hook handler that returns CONTINUE. */
    public static class ContinueHandler {
        boolean called = false;

        @PreReasoning
        public HookResult<PreReasoningEvent> onPreReasoning(PreReasoningEvent event) {
            called = true;
            return HookResult.proceed(event);
        }
    }

    /** A hook handler that returns SKIP. */
    public static class SkipHandler {
        boolean called = false;

        @PreActing
        public HookResult<PreActingEvent> onPreActing(PreActingEvent event) {
            called = true;
            return HookResult.skip(event, "skip reason");
        }
    }

    /** A hook handler that returns ABORT. */
    public static class AbortHandler {
        boolean called = false;

        @PreActing
        public HookResult<PreActingEvent> onPreActing(PreActingEvent event) {
            called = true;
            return HookResult.abort(event, "abort reason");
        }
    }

    /** A hook handler that returns INJECT with a message. */
    public static class InjectHandler {
        boolean called = false;
        final Msg injectedMsg;

        public InjectHandler(String content) {
            this.injectedMsg =
                    Msg.builder()
                            .role(MsgRole.ASSISTANT)
                            .addContent(new Content.TextContent(content))
                            .build();
        }

        @PreReasoning
        public HookResult<PreReasoningEvent> onPreReasoning(PreReasoningEvent event) {
            called = true;
            return HookResult.inject(event, injectedMsg, "inject-handler");
        }
    }

    /** A hook handler that returns MODIFY with changed input. */
    public static class ModifyHandler {
        boolean called = false;
        final Map<String, Object> modifiedInput;

        public ModifyHandler(Map<String, Object> modifiedInput) {
            this.modifiedInput = modifiedInput;
        }

        @PreActing
        public HookResult<PreActingEvent> onPreActing(PreActingEvent event) {
            called = true;
            return HookResult.modify(event, modifiedInput);
        }
    }

    /** A hook handler that throws an exception. */
    public static class ThrowingHandler {
        boolean called = false;

        @PreReasoning
        public HookResult<PreReasoningEvent> onPreReasoning(PreReasoningEvent event) {
            called = true;
            throw new RuntimeException("hook exception");
        }
    }

    /** A hook handler that tracks preReasoning context. */
    public static class PreReasoningContextTracker {
        List<Msg> capturedMessages = new ArrayList<>();
        ModelConfig capturedConfig = null;
        boolean called = false;

        @PreReasoning
        public PreReasoningEvent onPreReasoning(PreReasoningEvent event) {
            called = true;
            capturedMessages = new ArrayList<>(event.messages());
            capturedConfig = event.config();
            return event;
        }
    }

    /** A hook handler that tracks postActing tool result. */
    public static class PostActingResultTracker {
        ToolResult capturedResult = null;
        String capturedToolName = null;
        boolean called = false;

        @PostActing
        public PostActingEvent onPostActing(PostActingEvent event) {
            called = true;
            capturedToolName = event.toolName();
            capturedResult = event.result();
            return event;
        }
    }

    /** A hook handler that tracks session start. */
    public static class SessionStartTracker {
        boolean called = false;
        String capturedAgentName = null;
        Msg capturedInput = null;

        @OnSessionStart
        public void onSessionStart(SessionStartEvent event) {
            called = true;
            capturedAgentName = event.agentName();
            capturedInput = event.input();
        }
    }

    /** A hook handler that tracks session end. */
    public static class SessionEndTracker {
        boolean called = false;
        String capturedAgentName = null;
        AgentState capturedFinalState = null;

        @OnSessionEnd
        public void onSessionEnd(SessionEndEvent event) {
            called = true;
            capturedAgentName = event.agentName();
            capturedFinalState = event.finalState();
        }
    }

    // ================================
    //  Test 1: singleHook_CONTINUE_proceedsToLLM
    // ================================

    @Test
    @DisplayName("Single hook returning CONTINUE allows normal LLM call")
    void singleHook_CONTINUE_proceedsToLLM() {
        ContinueHandler handler = new ContinueHandler();
        chain.register(handler);

        ModelConfig config = ModelConfig.builder().model("test-model").build();
        PreReasoningEvent event = new PreReasoningEvent(List.of(), config, false);

        StepVerifier.create(chain.firePreReasoningWithResult(event))
                .assertNext(
                        result -> {
                            assertTrue(handler.called, "Hook should have been called");
                            assertEquals(HookResult.Decision.CONTINUE, result.decision());
                            assertTrue(result.shouldProceed(), "Should proceed to LLM");
                        })
                .verifyComplete();
    }

    // ================================
    //  Test 2: singleHook_SKIP_skipsLLM
    // ================================

    @Test
    @DisplayName("Single hook returning SKIP skips LLM call")
    void singleHook_SKIP_skipsLLM() {
        SkipHandler handler = new SkipHandler();
        chain.register(handler);

        PreActingEvent event =
                new PreActingEvent("stub_read", Map.of("path", "/tmp/test.txt"), false);

        StepVerifier.create(chain.firePreActingWithResult(event))
                .assertNext(
                        result -> {
                            assertTrue(handler.called, "Hook should have been called");
                            assertEquals(HookResult.Decision.SKIP, result.decision());
                            assertTrue(result.shouldSkip(), "Should skip operation");
                            assertEquals("skip reason", result.reason());
                        })
                .verifyComplete();
    }

    // ================================
    //  Test 3: singleHook_ABORT_terminatesAgent
    // ================================

    @Test
    @DisplayName("Single hook returning ABORT terminates agent")
    void singleHook_ABORT_terminatesAgent() {
        AbortHandler handler = new AbortHandler();
        chain.register(handler);

        PreActingEvent event =
                new PreActingEvent("stub_bash", Map.of("command", "echo hello"), false);

        StepVerifier.create(chain.firePreActingWithResult(event))
                .assertNext(
                        result -> {
                            assertTrue(handler.called, "Hook should have been called");
                            assertEquals(HookResult.Decision.ABORT, result.decision());
                            assertFalse(result.shouldProceed(), "Should not proceed");
                            assertEquals("abort reason", result.reason());
                        })
                .verifyComplete();
    }

    // ================================
    //  Test 4: singleHook_INJECT_injectsMessage
    // ================================

    @Test
    @DisplayName("Single hook returning INJECT injects message into context")
    void singleHook_INJECT_injectsMessage() {
        InjectHandler handler = new InjectHandler("injected context");
        chain.register(handler);

        ModelConfig config = ModelConfig.builder().model("test-model").build();
        PreReasoningEvent event = new PreReasoningEvent(List.of(), config, false);

        StepVerifier.create(chain.firePreReasoningWithResult(event))
                .assertNext(
                        result -> {
                            assertTrue(handler.called, "Hook should have been called");
                            assertEquals(HookResult.Decision.INJECT, result.decision());
                            assertTrue(result.hasInjectedMessage(), "Should have injected message");
                            assertEquals("injected context", result.injectedMessage().text());
                            assertEquals("inject-handler", result.hookSource());
                        })
                .verifyComplete();
    }

    // ================================
    //  Test 5: singleHook_MODIFY_changesPrompt
    // ================================

    @Test
    @DisplayName("Single hook returning MODIFY changes tool input")
    void singleHook_MODIFY_changesPrompt() {
        Map<String, Object> modifiedInput = Map.of("path", "/modified/path.txt");
        ModifyHandler handler = new ModifyHandler(modifiedInput);
        chain.register(handler);

        PreActingEvent event =
                new PreActingEvent("stub_read", Map.of("path", "/original/path.txt"), false);

        StepVerifier.create(chain.firePreActingWithResult(event))
                .assertNext(
                        result -> {
                            assertTrue(handler.called, "Hook should have been called");
                            assertEquals(HookResult.Decision.MODIFY, result.decision());
                            assertTrue(result.hasModifiedInput(), "Should have modified input");
                            assertEquals("/modified/path.txt", result.modifiedInput().get("path"));
                        })
                .verifyComplete();
    }

    // ================================
    //  Test 6: multiHook_priorityMerge_ABORT_wins
    // ================================

    @Test
    @DisplayName("Multiple hooks: ABORT has highest priority and wins")
    void multiHook_priorityMerge_ABORT_wins() {
        // Register CONTINUE, SKIP, INJECT, then ABORT - ABORT should win
        chain.register(new ContinueHandler());
        chain.register(new SkipHandlerForPreActing());
        chain.register(new InjectHandlerForPreActing("injected"));
        AbortHandler abortHandler = new AbortHandler();
        chain.register(abortHandler);

        PreActingEvent event =
                new PreActingEvent("stub_read", Map.of("path", "/tmp/test.txt"), false);

        StepVerifier.create(chain.firePreActingWithResult(event))
                .assertNext(
                        result -> {
                            assertTrue(abortHandler.called, "AbortHandler should have been called");
                            assertEquals(HookResult.Decision.ABORT, result.decision());
                            assertFalse(result.shouldProceed());
                            assertEquals("abort reason", result.reason());
                        })
                .verifyComplete();
    }

    /** A SKIP handler for PreActing events. */
    public static class SkipHandlerForPreActing {
        boolean called = false;

        @PreActing
        public HookResult<PreActingEvent> onPreActing(PreActingEvent event) {
            called = true;
            return HookResult.skip(event, "skip from pre-acting");
        }
    }

    /** An INJECT handler for PreActing events. */
    public static class InjectHandlerForPreActing {
        boolean called = false;
        final Msg injectedMsg;

        public InjectHandlerForPreActing(String content) {
            this.injectedMsg =
                    Msg.builder()
                            .role(MsgRole.ASSISTANT)
                            .addContent(new Content.TextContent(content))
                            .build();
        }

        @PreActing
        public HookResult<PreActingEvent> onPreActing(PreActingEvent event) {
            called = true;
            return HookResult.inject(event, injectedMsg, "pre-acting-inject");
        }
    }

    // ================================
    //  Test 7: multiHook_priorityMerge_SKIP_over_INJECT
    // ================================

    @Test
    @DisplayName("Multiple hooks: SKIP has higher priority than INJECT")
    void multiHook_priorityMerge_SKIP_over_INJECT() {
        // Register INJECT first, then SKIP - SKIP should win
        InjectHandlerForPreActing injectHandler = new InjectHandlerForPreActing("injected");
        SkipHandlerForPreActing skipHandler = new SkipHandlerForPreActing();
        chain.register(injectHandler);
        chain.register(skipHandler);

        PreActingEvent event =
                new PreActingEvent("stub_read", Map.of("path", "/tmp/test.txt"), false);

        StepVerifier.create(chain.firePreActingWithResult(event))
                .assertNext(
                        result -> {
                            assertTrue(
                                    injectHandler.called, "InjectHandler should have been called");
                            assertTrue(skipHandler.called, "SkipHandler should have been called");
                            assertEquals(HookResult.Decision.SKIP, result.decision());
                            assertTrue(result.shouldSkip());
                            assertEquals("skip from pre-acting", result.reason());
                        })
                .verifyComplete();
    }

    // ================================
    //  Test 8: hookThrowsException_isolated
    // ================================

    @Test
    @DisplayName("Hook throwing exception is isolated and does not affect other hooks")
    void hookThrowsException_isolated() {
        ThrowingHandler throwingHandler = new ThrowingHandler();
        ContinueHandler continueHandler = new ContinueHandler();
        chain.register(throwingHandler);
        chain.register(continueHandler);

        ModelConfig config = ModelConfig.builder().model("test-model").build();
        PreReasoningEvent event = new PreReasoningEvent(List.of(), config, false);

        // The exception should propagate as a Mono error
        StepVerifier.create(chain.firePreReasoningWithResult(event))
                .expectErrorMatches(
                        e ->
                                e instanceof RuntimeException
                                        && e.getMessage().contains("hook exception"))
                .verify();

        // The throwing handler should have been called before the exception
        assertTrue(throwingHandler.called, "ThrowingHandler should have been called");
    }

    // ================================
    //  Test 9: preReasoningHook_receivesCorrectContext
    // ================================

    @Test
    @DisplayName("PreReasoning hook receives correct messages and config")
    void preReasoningHook_receivesCorrectContext() {
        PreReasoningContextTracker tracker = new PreReasoningContextTracker();
        chain.register(tracker);

        List<Msg> messages =
                List.of(
                        Msg.builder()
                                .role(MsgRole.USER)
                                .addContent(new Content.TextContent("test message"))
                                .build());
        ModelConfig config = ModelConfig.builder().model("test-model").temperature(0.7).build();
        PreReasoningEvent event = new PreReasoningEvent(messages, config, false);

        StepVerifier.create(chain.firePreReasoning(event))
                .assertNext(
                        result -> {
                            assertTrue(tracker.called, "Hook should have been called");
                            assertEquals(1, tracker.capturedMessages.size());
                            assertEquals("test message", tracker.capturedMessages.get(0).text());
                            assertEquals("test-model", tracker.capturedConfig.model());
                            assertEquals(0.7, tracker.capturedConfig.temperature());
                        })
                .verifyComplete();
    }

    // ================================
    //  Test 10: postActingHook_receivesToolResult
    // ================================

    @Test
    @DisplayName("PostActing hook receives tool execution result")
    void postActingHook_receivesToolResult() {
        PostActingResultTracker tracker = new PostActingResultTracker();
        chain.register(tracker);

        ToolResult result =
                new ToolResult("tu-1", "file contents", false, Map.of("path", "/tmp/test.txt"));
        PostActingEvent event = new PostActingEvent("stub_read", result);

        StepVerifier.create(chain.firePostActing(event))
                .assertNext(
                        res -> {
                            assertTrue(tracker.called, "Hook should have been called");
                            assertEquals("stub_read", tracker.capturedToolName);
                            assertNotNull(tracker.capturedResult);
                            assertEquals("file contents", tracker.capturedResult.content());
                            assertFalse(tracker.capturedResult.isError());
                        })
                .verifyComplete();
    }

    // ================================
    //  Test 11: sessionLifecycleHook_onSessionStart
    // ================================

    @Test
    @DisplayName("Session start lifecycle hook is invoked")
    void sessionLifecycleHook_onSessionStart() {
        SessionStartTracker tracker = new SessionStartTracker();
        chain.register(tracker);

        Msg userInput =
                Msg.builder()
                        .role(MsgRole.USER)
                        .addContent(new Content.TextContent("test input"))
                        .build();
        SessionStartEvent event = new SessionStartEvent("test-agent", userInput, "gpt-4", 10);

        StepVerifier.create(chain.fireOnSessionStart(event))
                .assertNext(
                        result -> {
                            assertTrue(tracker.called, "SessionStart hook should have been called");
                            assertEquals("test-agent", tracker.capturedAgentName);
                            assertNotNull(tracker.capturedInput);
                            assertEquals("test input", tracker.capturedInput.text());
                        })
                .verifyComplete();
    }

    // ================================
    //  Test 12: sessionLifecycleHook_onSessionEnd
    // ================================

    @Test
    @DisplayName("Session end lifecycle hook is invoked")
    void sessionLifecycleHook_onSessionEnd() {
        SessionEndTracker tracker = new SessionEndTracker();
        chain.register(tracker);

        SessionEndEvent event =
                new SessionEndEvent(
                        "test-agent", AgentState.COMPLETED, 5, 1200L, Duration.ofSeconds(30), null);

        StepVerifier.create(chain.fireOnSessionEnd(event))
                .assertNext(
                        result -> {
                            assertTrue(tracker.called, "SessionEnd hook should have been called");
                            assertEquals("test-agent", tracker.capturedAgentName);
                            assertEquals(AgentState.COMPLETED, tracker.capturedFinalState);
                        })
                .verifyComplete();
    }
}
