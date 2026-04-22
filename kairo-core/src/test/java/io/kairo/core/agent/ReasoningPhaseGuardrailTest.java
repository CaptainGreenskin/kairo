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
import io.kairo.api.guardrail.*;
import io.kairo.api.hook.HookChain;
import io.kairo.api.message.Content;
import io.kairo.api.message.Msg;
import io.kairo.api.message.MsgRole;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.core.context.TokenBudgetManager;
import io.kairo.core.hook.DefaultHookChain;
import io.kairo.core.model.ModelFallbackManager;
import io.kairo.core.shutdown.GracefulShutdownManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Tests for PRE_MODEL/POST_MODEL guardrail interception and GuardrailDenyException flow control in
 * {@link ReasoningPhase}.
 *
 * <p>Validates that the guardrail chain is correctly invoked at PRE_MODEL (before model call) and
 * POST_MODEL (after model response), and that deny/allow/modify decisions are properly handled.
 */
class ReasoningPhaseGuardrailTest {

    private ModelProvider modelProvider;
    private ToolExecutor toolExecutor;
    private HookChain hookChain;
    private GracefulShutdownManager shutdownManager;
    private TokenBudgetManager tokenBudgetManager;
    private ErrorRecoveryStrategy errorRecovery;
    private AtomicBoolean interrupted;
    private AtomicInteger currentIteration;
    private AtomicLong totalTokensUsed;

    @BeforeEach
    void setUp() {
        modelProvider = mock(ModelProvider.class);
        toolExecutor = mock(ToolExecutor.class);
        hookChain = new DefaultHookChain();
        shutdownManager = new GracefulShutdownManager();
        tokenBudgetManager = new TokenBudgetManager(200_000, 8_096);
        errorRecovery =
                new ErrorRecoveryStrategy(modelProvider, null, new ModelFallbackManager(List.of()));
        interrupted = new AtomicBoolean(false);
        currentIteration = new AtomicInteger(0);
        totalTokensUsed = new AtomicLong(0);
    }

    private ReActLoop createLoop(GuardrailChain guardrailChain) {
        AgentConfig config =
                AgentConfig.builder()
                        .name("guardrail-test-agent")
                        .modelProvider(modelProvider)
                        .modelName("test-model")
                        .maxIterations(10)
                        .tokenBudget(200_000)
                        .build();

        ReActLoopContext ctx =
                new ReActLoopContext(
                        "agent-g1",
                        "guardrail-test-agent",
                        config,
                        hookChain,
                        null, // tracer
                        toolExecutor,
                        errorRecovery,
                        tokenBudgetManager,
                        shutdownManager,
                        null, // contextManager
                        guardrailChain);

        ModelConfig modelConfig =
                ModelConfig.builder()
                        .model("test-model")
                        .maxTokens(4096)
                        .temperature(0.7)
                        .tools(List.of())
                        .build();

        return new ReActLoop(
                ctx, interrupted, currentIteration, totalTokensUsed, () -> modelConfig);
    }

    private ModelResponse textResponse(String text) {
        return new ModelResponse(
                "resp-1",
                List.of(new Content.TextContent(text)),
                new ModelResponse.Usage(10, 20, 0, 0),
                ModelResponse.StopReason.END_TURN,
                "test-model");
    }

    // ========================================================================
    // Suite 1: PRE_MODEL / POST_MODEL Guardrail Interception
    // ========================================================================

    @Nested
    @DisplayName("Suite 1: PRE_MODEL/POST_MODEL Guardrail Interception")
    class GuardrailInterceptionTests {

        @Test
        @DisplayName("PRE_MODEL DENY prevents model call")
        void preModelDenyPreventsModelCall() {
            GuardrailChain chain = mock(GuardrailChain.class);
            when(chain.evaluate(any(GuardrailContext.class)))
                    .thenAnswer(
                            inv -> {
                                GuardrailContext gc = inv.getArgument(0);
                                if (gc.phase() == GuardrailPhase.PRE_MODEL) {
                                    return Mono.just(
                                            GuardrailDecision.deny(
                                                    "Content policy violation", "test-policy"));
                                }
                                return Mono.just(GuardrailDecision.allow("test-policy"));
                            });

            ReActLoop loop = createLoop(chain);
            loop.injectMessages(List.of(Msg.of(MsgRole.USER, "bad content")));

            StepVerifier.create(loop.runLoop())
                    .assertNext(
                            msg -> {
                                assertEquals(MsgRole.ASSISTANT, msg.role());
                                assertTrue(
                                        msg.text().contains("blocked by guardrail"),
                                        "Expected guardrail block message, got: " + msg.text());
                            })
                    .verifyComplete();

            // Model provider must never be called when PRE_MODEL denies
            verify(modelProvider, never()).call(anyList(), any(ModelConfig.class));
        }

        @Test
        @DisplayName("PRE_MODEL ALLOW proceeds to model call")
        void preModelAllowProceedsToModel() {
            GuardrailChain chain = mock(GuardrailChain.class);
            when(chain.evaluate(any(GuardrailContext.class)))
                    .thenReturn(Mono.just(GuardrailDecision.allow("test-policy")));

            when(modelProvider.call(anyList(), any(ModelConfig.class)))
                    .thenReturn(Mono.just(textResponse("Model called successfully.")));

            ReActLoop loop = createLoop(chain);
            loop.injectMessages(List.of(Msg.of(MsgRole.USER, "good content")));

            StepVerifier.create(loop.runLoop())
                    .assertNext(
                            msg -> {
                                assertEquals(MsgRole.ASSISTANT, msg.role());
                                assertTrue(msg.text().contains("Model called successfully."));
                            })
                    .verifyComplete();

            // Model must have been called
            verify(modelProvider, times(1)).call(anyList(), any(ModelConfig.class));
        }

        @Test
        @DisplayName("POST_MODEL DENY discards response and returns guardrail block message")
        void postModelDenyDiscardsResponse() {
            GuardrailChain chain = mock(GuardrailChain.class);
            when(chain.evaluate(any(GuardrailContext.class)))
                    .thenAnswer(
                            inv -> {
                                GuardrailContext gc = inv.getArgument(0);
                                if (gc.phase() == GuardrailPhase.PRE_MODEL) {
                                    return Mono.just(GuardrailDecision.allow("test-policy"));
                                }
                                // POST_MODEL: deny the response
                                return Mono.just(
                                        GuardrailDecision.deny(
                                                "Response contains PII", "pii-policy"));
                            });

            when(modelProvider.call(anyList(), any(ModelConfig.class)))
                    .thenReturn(Mono.just(textResponse("Here is some PII data.")));

            ReActLoop loop = createLoop(chain);
            loop.injectMessages(List.of(Msg.of(MsgRole.USER, "give me data")));

            // POST_MODEL deny now follows the same degradation path as PRE_MODEL deny
            StepVerifier.create(loop.runLoop())
                    .assertNext(
                            msg -> {
                                assertEquals(MsgRole.ASSISTANT, msg.role());
                                assertTrue(
                                        msg.text().contains("blocked by guardrail"),
                                        "Expected guardrail block message, got: " + msg.text());
                            })
                    .verifyComplete();

            // Model was called (PRE_MODEL allowed), but response was denied at POST_MODEL
            verify(modelProvider, times(1)).call(anyList(), any(ModelConfig.class));
        }

        @Test
        @DisplayName("PRE_MODEL MODIFY alters messages and config sent to model")
        void preModelModifyAltersContext() {
            List<Msg> modifiedMessages = List.of(Msg.of(MsgRole.USER, "sanitized content"));
            ModelConfig modifiedConfig =
                    ModelConfig.builder()
                            .model("safer-model")
                            .maxTokens(2048)
                            .temperature(0.3)
                            .tools(List.of())
                            .build();

            GuardrailChain chain = mock(GuardrailChain.class);
            when(chain.evaluate(any(GuardrailContext.class)))
                    .thenAnswer(
                            inv -> {
                                GuardrailContext gc = inv.getArgument(0);
                                if (gc.phase() == GuardrailPhase.PRE_MODEL) {
                                    return Mono.just(
                                            GuardrailDecision.modify(
                                                    new GuardrailPayload.ModelInput(
                                                            modifiedMessages, modifiedConfig),
                                                    "sanitized input",
                                                    "sanitizer-policy"));
                                }
                                // POST_MODEL: allow
                                return Mono.just(GuardrailDecision.allow("test-policy"));
                            });

            when(modelProvider.call(anyList(), any(ModelConfig.class)))
                    .thenAnswer(
                            inv -> {
                                List<Msg> msgs = inv.getArgument(0);
                                ModelConfig cfg = inv.getArgument(1);
                                // Verify the modified messages and config were passed
                                assertEquals(1, msgs.size());
                                assertTrue(msgs.get(0).text().contains("sanitized content"));
                                assertEquals("safer-model", cfg.model());
                                assertEquals(2048, cfg.maxTokens());
                                return Mono.just(textResponse("Processed sanitized input."));
                            });

            ReActLoop loop = createLoop(chain);
            loop.injectMessages(List.of(Msg.of(MsgRole.USER, "original content")));

            StepVerifier.create(loop.runLoop())
                    .assertNext(
                            msg -> {
                                assertEquals(MsgRole.ASSISTANT, msg.role());
                                assertTrue(msg.text().contains("Processed sanitized input."));
                            })
                    .verifyComplete();

            verify(modelProvider, times(1)).call(anyList(), any(ModelConfig.class));
        }

        @Test
        @DisplayName("POST_MODEL MODIFY replaces model response")
        void postModelModifyReplacesResponse() {
            ModelResponse replacementResponse =
                    new ModelResponse(
                            "resp-modified",
                            List.of(new Content.TextContent("Redacted response.")),
                            new ModelResponse.Usage(10, 20, 0, 0),
                            ModelResponse.StopReason.END_TURN,
                            "test-model");

            GuardrailChain chain = mock(GuardrailChain.class);
            when(chain.evaluate(any(GuardrailContext.class)))
                    .thenAnswer(
                            inv -> {
                                GuardrailContext gc = inv.getArgument(0);
                                if (gc.phase() == GuardrailPhase.PRE_MODEL) {
                                    return Mono.just(GuardrailDecision.allow("test-policy"));
                                }
                                // POST_MODEL: modify the response
                                return Mono.just(
                                        GuardrailDecision.modify(
                                                new GuardrailPayload.ModelOutput(
                                                        replacementResponse),
                                                "redacted PII",
                                                "pii-policy"));
                            });

            when(modelProvider.call(anyList(), any(ModelConfig.class)))
                    .thenReturn(Mono.just(textResponse("Original with PII.")));

            ReActLoop loop = createLoop(chain);
            loop.injectMessages(List.of(Msg.of(MsgRole.USER, "get data")));

            StepVerifier.create(loop.runLoop())
                    .assertNext(
                            msg -> {
                                assertEquals(MsgRole.ASSISTANT, msg.role());
                                assertTrue(
                                        msg.text().contains("Redacted response."),
                                        "Expected modified response, got: " + msg.text());
                                assertFalse(
                                        msg.text().contains("Original with PII"),
                                        "Original response should be replaced");
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Null guardrail chain proceeds normally without interception")
        void nullGuardrailChainProceedsNormally() {
            when(modelProvider.call(anyList(), any(ModelConfig.class)))
                    .thenReturn(Mono.just(textResponse("No guardrail interference.")));

            ReActLoop loop = createLoop(null); // null guardrail chain
            loop.injectMessages(List.of(Msg.of(MsgRole.USER, "hello")));

            StepVerifier.create(loop.runLoop())
                    .assertNext(
                            msg -> {
                                assertEquals(MsgRole.ASSISTANT, msg.role());
                                assertTrue(msg.text().contains("No guardrail interference."));
                            })
                    .verifyComplete();

            verify(modelProvider, times(1)).call(anyList(), any(ModelConfig.class));
        }

        @Test
        @DisplayName("Guardrail chain receives correct phase and payload for PRE_MODEL")
        void guardrailChainReceivesCorrectPreModelContext() {
            GuardrailChain chain = mock(GuardrailChain.class);
            when(chain.evaluate(any(GuardrailContext.class)))
                    .thenAnswer(
                            inv -> {
                                GuardrailContext gc = inv.getArgument(0);
                                if (gc.phase() == GuardrailPhase.PRE_MODEL) {
                                    assertEquals("guardrail-test-agent", gc.agentName());
                                    assertEquals("test-model", gc.targetName());
                                    assertInstanceOf(
                                            GuardrailPayload.ModelInput.class, gc.payload());
                                    GuardrailPayload.ModelInput input =
                                            (GuardrailPayload.ModelInput) gc.payload();
                                    assertFalse(input.messages().isEmpty());
                                    assertNotNull(input.config());
                                }
                                return Mono.just(GuardrailDecision.allow("test-policy"));
                            });

            when(modelProvider.call(anyList(), any(ModelConfig.class)))
                    .thenReturn(Mono.just(textResponse("OK")));

            ReActLoop loop = createLoop(chain);
            loop.injectMessages(List.of(Msg.of(MsgRole.USER, "test")));

            StepVerifier.create(loop.runLoop())
                    .assertNext(msg -> assertEquals(MsgRole.ASSISTANT, msg.role()))
                    .verifyComplete();

            // Verify chain was called at least for PRE_MODEL and POST_MODEL
            verify(chain, atLeast(2)).evaluate(any(GuardrailContext.class));
        }

        @Test
        @DisplayName("Guardrail chain receives correct phase and payload for POST_MODEL")
        void guardrailChainReceivesCorrectPostModelContext() {
            GuardrailChain chain = mock(GuardrailChain.class);
            when(chain.evaluate(any(GuardrailContext.class)))
                    .thenAnswer(
                            inv -> {
                                GuardrailContext gc = inv.getArgument(0);
                                if (gc.phase() == GuardrailPhase.POST_MODEL) {
                                    assertEquals("guardrail-test-agent", gc.agentName());
                                    assertInstanceOf(
                                            GuardrailPayload.ModelOutput.class, gc.payload());
                                    GuardrailPayload.ModelOutput output =
                                            (GuardrailPayload.ModelOutput) gc.payload();
                                    assertNotNull(output.response());
                                    assertEquals("test-model", output.response().model());
                                }
                                return Mono.just(GuardrailDecision.allow("test-policy"));
                            });

            when(modelProvider.call(anyList(), any(ModelConfig.class)))
                    .thenReturn(Mono.just(textResponse("Response to check")));

            ReActLoop loop = createLoop(chain);
            loop.injectMessages(List.of(Msg.of(MsgRole.USER, "test")));

            StepVerifier.create(loop.runLoop())
                    .assertNext(msg -> assertEquals(MsgRole.ASSISTANT, msg.role()))
                    .verifyComplete();

            verify(chain, atLeast(2)).evaluate(any(GuardrailContext.class));
        }
    }

    // ========================================================================
    // Suite 2: GuardrailDenyException Flow Control
    // ========================================================================

    @Nested
    @DisplayName("Suite 2: GuardrailDenyException Flow Control")
    class DenyExceptionFlowControlTests {

        @Test
        @DisplayName("PRE_MODEL deny exception carries reason from GuardrailDecision")
        void denyExceptionCarriesReason() {
            String denyReason = "Prohibited content detected";

            GuardrailChain chain = mock(GuardrailChain.class);
            when(chain.evaluate(any(GuardrailContext.class)))
                    .thenAnswer(
                            inv -> {
                                GuardrailContext gc = inv.getArgument(0);
                                if (gc.phase() == GuardrailPhase.PRE_MODEL) {
                                    return Mono.just(
                                            GuardrailDecision.deny(denyReason, "content-policy"));
                                }
                                return Mono.just(GuardrailDecision.allow("test-policy"));
                            });

            ReActLoop loop = createLoop(chain);
            loop.injectMessages(List.of(Msg.of(MsgRole.USER, "prohibited input")));

            StepVerifier.create(loop.runLoop())
                    .assertNext(
                            msg -> {
                                assertEquals(MsgRole.ASSISTANT, msg.role());
                                // The exception is caught internally and converted to a response
                                // It should NOT propagate to the caller
                                assertNotNull(msg.text());
                            })
                    .verifyComplete();

            // Verify model was never called — deny happened before model invocation
            verify(modelProvider, never()).call(anyList(), any(ModelConfig.class));
        }

        @Test
        @DisplayName("POST_MODEL deny returns degradation response (same as PRE_MODEL)")
        void postModelDenyReturnsDegradationResponse() {
            String denyReason = "Response contains sensitive data";

            GuardrailChain chain = mock(GuardrailChain.class);
            when(chain.evaluate(any(GuardrailContext.class)))
                    .thenAnswer(
                            inv -> {
                                GuardrailContext gc = inv.getArgument(0);
                                if (gc.phase() == GuardrailPhase.PRE_MODEL) {
                                    return Mono.just(GuardrailDecision.allow("test-policy"));
                                }
                                return Mono.just(GuardrailDecision.deny(denyReason, "data-policy"));
                            });

            when(modelProvider.call(anyList(), any(ModelConfig.class)))
                    .thenReturn(Mono.just(textResponse("Sensitive data here.")));

            ReActLoop loop = createLoop(chain);
            loop.injectMessages(List.of(Msg.of(MsgRole.USER, "get sensitive info")));

            // POST_MODEL deny now returns degradation response, not an error
            StepVerifier.create(loop.runLoop())
                    .assertNext(
                            msg -> {
                                assertEquals(MsgRole.ASSISTANT, msg.role());
                                assertTrue(
                                        msg.text().contains("blocked by guardrail"),
                                        "Expected guardrail block message, got: " + msg.text());
                            })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Deny exception is caught internally and does not propagate to callers")
        void denyExceptionCaughtInternally() {
            GuardrailChain chain = mock(GuardrailChain.class);
            when(chain.evaluate(any(GuardrailContext.class)))
                    .thenAnswer(
                            inv -> {
                                GuardrailContext gc = inv.getArgument(0);
                                if (gc.phase() == GuardrailPhase.PRE_MODEL) {
                                    return Mono.just(
                                            GuardrailDecision.deny("blocked", "blocker-policy"));
                                }
                                return Mono.just(GuardrailDecision.allow("test-policy"));
                            });

            ReActLoop loop = createLoop(chain);
            loop.injectMessages(List.of(Msg.of(MsgRole.USER, "blocked request")));

            // The Mono should complete normally with a message, NOT error
            StepVerifier.create(loop.runLoop())
                    .assertNext(
                            msg -> {
                                // Verify we get a proper Msg, not an exception
                                assertNotNull(msg);
                                assertEquals(MsgRole.ASSISTANT, msg.role());
                            })
                    .verifyComplete(); // Must complete, not error
        }

        @Test
        @DisplayName(
                "Reactive chain terminates cleanly on PRE_MODEL deny — no partial model execution")
        void reactiveChainTerminatesCleanlyOnPreModelDeny() {
            AtomicBoolean modelCalled = new AtomicBoolean(false);

            GuardrailChain chain = mock(GuardrailChain.class);
            when(chain.evaluate(any(GuardrailContext.class)))
                    .thenAnswer(
                            inv -> {
                                GuardrailContext gc = inv.getArgument(0);
                                if (gc.phase() == GuardrailPhase.PRE_MODEL) {
                                    return Mono.just(GuardrailDecision.deny("blocked", "policy"));
                                }
                                return Mono.just(GuardrailDecision.allow("test-policy"));
                            });

            when(modelProvider.call(anyList(), any(ModelConfig.class)))
                    .thenAnswer(
                            inv -> {
                                modelCalled.set(true);
                                return Mono.just(textResponse("Should never appear."));
                            });

            ReActLoop loop = createLoop(chain);
            loop.injectMessages(List.of(Msg.of(MsgRole.USER, "test")));

            StepVerifier.create(loop.runLoop())
                    .assertNext(msg -> assertNotNull(msg))
                    .verifyComplete();

            // Verify no partial model execution
            assertFalse(
                    modelCalled.get(), "Model should not have been called after PRE_MODEL deny");
            // Verify no tool execution side effects
            verifyNoInteractions(toolExecutor);
        }

        @Test
        @DisplayName(
                "Reactive chain terminates cleanly on POST_MODEL deny — no downstream processing")
        void reactiveChainTerminatesCleanlyOnPostModelDeny() {
            GuardrailChain chain = mock(GuardrailChain.class);
            when(chain.evaluate(any(GuardrailContext.class)))
                    .thenAnswer(
                            inv -> {
                                GuardrailContext gc = inv.getArgument(0);
                                if (gc.phase() == GuardrailPhase.PRE_MODEL) {
                                    return Mono.just(GuardrailDecision.allow("test-policy"));
                                }
                                // POST_MODEL deny
                                return Mono.just(
                                        GuardrailDecision.deny("unsafe response", "safety-policy"));
                            });

            // Model returns tool calls — but POST_MODEL deny should prevent tool execution
            when(modelProvider.call(anyList(), any(ModelConfig.class)))
                    .thenReturn(
                            Mono.just(
                                    new ModelResponse(
                                            "resp-tool",
                                            List.of(
                                                    new Content.ToolUseContent(
                                                            "tc-1", "dangerous_tool", Map.of())),
                                            new ModelResponse.Usage(10, 20, 0, 0),
                                            ModelResponse.StopReason.TOOL_USE,
                                            "test-model")));

            ReActLoop loop = createLoop(chain);
            loop.injectMessages(List.of(Msg.of(MsgRole.USER, "execute something")));

            // POST_MODEL deny now returns degradation response, not an error
            StepVerifier.create(loop.runLoop())
                    .assertNext(
                            msg -> {
                                assertEquals(MsgRole.ASSISTANT, msg.role());
                                assertTrue(
                                        msg.text().contains("blocked by guardrail"),
                                        "Expected guardrail block message, got: " + msg.text());
                            })
                    .verifyComplete();

            // Tool executor must never be called — POST_MODEL deny stops the pipeline
            verifyNoInteractions(toolExecutor);
        }

        @Test
        @DisplayName("PRE_MODEL WARN allows pipeline to proceed")
        void preModelWarnAllowsPipelineToProceed() {
            GuardrailChain chain = mock(GuardrailChain.class);
            when(chain.evaluate(any(GuardrailContext.class)))
                    .thenAnswer(
                            inv -> {
                                GuardrailContext gc = inv.getArgument(0);
                                if (gc.phase() == GuardrailPhase.PRE_MODEL) {
                                    return Mono.just(
                                            GuardrailDecision.warn(
                                                    "Suspicious input", "warn-policy"));
                                }
                                return Mono.just(GuardrailDecision.allow("test-policy"));
                            });

            when(modelProvider.call(anyList(), any(ModelConfig.class)))
                    .thenReturn(Mono.just(textResponse("Proceeded despite warning.")));

            ReActLoop loop = createLoop(chain);
            loop.injectMessages(List.of(Msg.of(MsgRole.USER, "slightly suspicious input")));

            StepVerifier.create(loop.runLoop())
                    .assertNext(
                            msg -> {
                                assertEquals(MsgRole.ASSISTANT, msg.role());
                                assertTrue(msg.text().contains("Proceeded despite warning."));
                            })
                    .verifyComplete();

            verify(modelProvider, times(1)).call(anyList(), any(ModelConfig.class));
        }
    }
}
