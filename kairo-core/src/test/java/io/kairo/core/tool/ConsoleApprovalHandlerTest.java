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
package io.kairo.core.tool;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.tool.ToolCallRequest;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.core.tool.ConsoleApprovalHandler.ApprovalDecision;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ConsoleApprovalHandlerTest {

    private static final ToolCallRequest BASH_REQUEST =
            new ToolCallRequest(
                    "BashTool", Map.of("command", "rm -rf target/"), ToolSideEffect.SYSTEM_CHANGE);

    private static final ToolCallRequest WRITE_REQUEST =
            new ToolCallRequest("WriteTool", Map.of("path", "/tmp/out.txt"), ToolSideEffect.WRITE);

    private ConsoleApprovalHandler handlerWithInput(String input) {
        BufferedReader reader = new BufferedReader(new StringReader(input));
        PrintWriter writer = new PrintWriter(new StringWriter());
        return new ConsoleApprovalHandler(reader, writer);
    }

    @Test
    void yesResponse_returnsAllow() {
        var handler = handlerWithInput("y\n");

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(
                        result -> {
                            assertThat(result.approved()).isTrue();
                            assertThat(result.reason()).isNull();
                        })
                .verifyComplete();
    }

    @Test
    void yesFullResponse_returnsAllow() {
        var handler = handlerWithInput("yes\n");

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(result -> assertThat(result.approved()).isTrue())
                .verifyComplete();
    }

    @Test
    void noResponse_returnsDenied() {
        var handler = handlerWithInput("n\n");

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(
                        result -> {
                            assertThat(result.approved()).isFalse();
                            assertThat(result.reason()).isEqualTo("User denied");
                        })
                .verifyComplete();
    }

    @Test
    void noFullResponse_returnsDenied() {
        var handler = handlerWithInput("no\n");

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(
                        result -> {
                            assertThat(result.approved()).isFalse();
                            assertThat(result.reason()).isEqualTo("User denied");
                        })
                .verifyComplete();
    }

    @Test
    void alwaysResponse_allowsAndRemembersForSubsequentCalls() {
        var handler = handlerWithInput("a\n");

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(result -> assertThat(result.approved()).isTrue())
                .verifyComplete();

        // Second invocation — should auto-approve without prompting
        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(
                        result -> {
                            assertThat(result.approved()).isTrue();
                            assertThat(result.reason()).isNull();
                        })
                .verifyComplete();
    }

    @Test
    void alwaysFullResponse_allowsAndRemembersForSubsequentCalls() {
        var handler = handlerWithInput("always\n");

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(result -> assertThat(result.approved()).isTrue())
                .verifyComplete();

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(result -> assertThat(result.approved()).isTrue())
                .verifyComplete();
    }

    @Test
    void neverResponse_deniesAndRemembersForSubsequentCalls() {
        var handler = handlerWithInput("v\n");

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(
                        result -> {
                            assertThat(result.approved()).isFalse();
                            assertThat(result.reason()).isEqualTo("User permanently denied");
                        })
                .verifyComplete();

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(
                        result -> {
                            assertThat(result.approved()).isFalse();
                            assertThat(result.reason()).isEqualTo("User permanently denied");
                        })
                .verifyComplete();
    }

    @Test
    void neverFullResponse_deniesAndRemembersForSubsequentCalls() {
        var handler = handlerWithInput("never\n");

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(result -> assertThat(result.approved()).isFalse())
                .verifyComplete();

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(result -> assertThat(result.approved()).isFalse())
                .verifyComplete();
    }

    @Test
    void emptyInput_returnsDenied() {
        var handler = handlerWithInput("\n");

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(
                        result -> {
                            assertThat(result.approved()).isFalse();
                            assertThat(result.reason()).isEqualTo("No response");
                        })
                .verifyComplete();
    }

    @Test
    void eofInput_returnsDenied() {
        // Empty string → readLine returns null (EOF)
        var handler = handlerWithInput("");

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(
                        result -> {
                            assertThat(result.approved()).isFalse();
                            assertThat(result.reason()).isEqualTo("No response");
                        })
                .verifyComplete();
    }

    @Test
    void resetApprovals_clearsMemory() {
        var handler = handlerWithInput("a\nn\n");

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(result -> assertThat(result.approved()).isTrue())
                .verifyComplete();

        assertThat(handler.getApprovalState()).containsKey("BashTool");

        handler.resetApprovals();
        assertThat(handler.getApprovalState()).isEmpty();

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(result -> assertThat(result.approved()).isFalse())
                .verifyComplete();
    }

    @Test
    void alwaysDecision_isScopedToSpecificTool() {
        var handler = handlerWithInput("a\nn\n");

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(result -> assertThat(result.approved()).isTrue())
                .verifyComplete();

        // WriteTool should still prompt — reads "n"
        StepVerifier.create(handler.requestApproval(WRITE_REQUEST))
                .assertNext(result -> assertThat(result.approved()).isFalse())
                .verifyComplete();
    }

    @Test
    void getApprovalState_returnsCurrentState() {
        var handler = handlerWithInput("a\n");

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(result -> assertThat(result.approved()).isTrue())
                .verifyComplete();

        Map<String, ApprovalDecision> state = handler.getApprovalState();
        assertThat(state).containsEntry("BashTool", ApprovalDecision.ALWAYS_ALLOW);
    }

    @Test
    void restoreApprovals_restoresFromSnapshot() {
        var handler = handlerWithInput("");

        Map<String, ApprovalDecision> snapshot =
                Map.of(
                        "BashTool", ApprovalDecision.ALWAYS_ALLOW,
                        "WriteTool", ApprovalDecision.ALWAYS_DENY);

        handler.restoreApprovals(snapshot);

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(result -> assertThat(result.approved()).isTrue())
                .verifyComplete();

        StepVerifier.create(handler.requestApproval(WRITE_REQUEST))
                .assertNext(
                        result -> {
                            assertThat(result.approved()).isFalse();
                            assertThat(result.reason()).isEqualTo("User permanently denied");
                        })
                .verifyComplete();
    }

    @Test
    void restoreApprovals_withNull_clearsState() {
        var handler = handlerWithInput("a\n");

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(result -> assertThat(result.approved()).isTrue())
                .verifyComplete();

        handler.restoreApprovals(null);
        assertThat(handler.getApprovalState()).isEmpty();
    }

    @Test
    void promptOutput_containsToolNameAndArgs() {
        StringWriter outputCapture = new StringWriter();
        PrintWriter writer = new PrintWriter(outputCapture);
        BufferedReader reader = new BufferedReader(new StringReader("y\n"));
        var handler = new ConsoleApprovalHandler(reader, writer);

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(result -> assertThat(result.approved()).isTrue())
                .verifyComplete();

        String output = outputCapture.toString();
        assertThat(output).contains("BashTool");
        assertThat(output).contains("rm -rf target/");
        assertThat(output).contains("SYSTEM_CHANGE");
        assertThat(output).contains("[y]es / [n]o / [a]lways / ne[v]er >");
    }

    @Test
    void invalidInput_returnsDenied() {
        var handler = handlerWithInput("maybe\n");

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .assertNext(
                        result -> {
                            assertThat(result.approved()).isFalse();
                            assertThat(result.reason()).isEqualTo("No response");
                        })
                .verifyComplete();
    }

    @Test
    void cancellation_interruptsBlockedReader() {
        // No input — handler will block in readLineInterruptibly's poll loop.
        // Disposing the subscription must trigger sink.onDispose → thread.interrupt
        // and complete the Mono within the cancel window (well under 1s).
        BufferedReader emptyReader =
                new BufferedReader(new StringReader("")) {
                    @Override
                    public boolean ready() {
                        return false; // simulate stdin with no available input
                    }
                };
        var handler = new ConsoleApprovalHandler(emptyReader, new PrintWriter(new StringWriter()));

        StepVerifier.create(handler.requestApproval(BASH_REQUEST))
                .thenAwait(Duration.ofMillis(100))
                .thenCancel()
                .verify(Duration.ofSeconds(2));
    }
}
