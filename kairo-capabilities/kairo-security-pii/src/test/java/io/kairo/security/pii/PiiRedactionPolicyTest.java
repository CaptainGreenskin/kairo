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
package io.kairo.security.pii;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.guardrail.GuardrailContext;
import io.kairo.api.guardrail.GuardrailDecision;
import io.kairo.api.guardrail.GuardrailPayload;
import io.kairo.api.guardrail.GuardrailPhase;
import io.kairo.api.message.Content;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.ToolResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PiiRedactionPolicy}.
 *
 * <p>Each shipped {@link PiiPattern} gets a positive case (string redacted) plus the policy is
 * exercised against the two payload variants it understands ({@link GuardrailPayload.ToolOutput}
 * and {@link GuardrailPayload.ModelOutput}). Phase skipping and non-redactable payloads
 * short-circuit cleanly.
 */
class PiiRedactionPolicyTest {

    private final PiiRedactionPolicy policy = PiiRedactionPolicy.stock();

    @Test
    void emailIsRedacted() {
        var result = policy.redactString("contact me at alice@example.com today");
        assertThat(result.text()).isEqualTo("contact me at <redacted:email> today");
        assertThat(result.matchCount()).isEqualTo(1);
    }

    @Test
    void usPhoneIsRedacted() {
        var result = policy.redactString("call (415) 555-1234 or 415.555.9876");
        assertThat(result.text()).contains("<redacted:phone>");
        assertThat(result.matchCount()).isEqualTo(2);
    }

    @Test
    void creditCardIsRedacted() {
        var result = policy.redactString("card 4111-1111-1111-1111 expires");
        assertThat(result.text()).contains("<redacted:cc>");
        assertThat(result.matchCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void ssnIsRedacted() {
        var result = policy.redactString("SSN 123-45-6789 confidential");
        assertThat(result.text()).isEqualTo("SSN <redacted:ssn> confidential");
        assertThat(result.matchCount()).isEqualTo(1);
    }

    @Test
    void apiKeyIsRedacted() {
        var result = policy.redactString("token sk-AbCdEfGhIjKlMnOpQrSt issued");
        assertThat(result.text()).isEqualTo("token <redacted:api-key> issued");
        assertThat(result.matchCount()).isEqualTo(1);
    }

    @Test
    void jwtIsRedacted() {
        var jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature_part_here";
        var result = policy.redactString("auth: " + jwt);
        assertThat(result.text()).contains("<redacted:jwt>");
        assertThat(result.matchCount()).isEqualTo(1);
    }

    @Test
    void emptyAndNullStringsAreNoop() {
        assertThat(policy.redactString("").matchCount()).isZero();
        assertThat(policy.redactString(null).matchCount()).isZero();
        assertThat(policy.redactString("nothing to see here").matchCount()).isZero();
    }

    @Test
    void multiplePatternsCompose() {
        var input = "user alice@example.com SSN 123-45-6789 token sk-XXXXXXXXXXXXXXXX";
        var result = policy.redactString(input);
        assertThat(result.text())
                .contains("<redacted:email>")
                .contains("<redacted:ssn>")
                .contains("<redacted:api-key>");
        assertThat(result.matchCount()).isEqualTo(3);
    }

    @Test
    void phaseNotInConfigSkipsEvaluation() {
        var preModel =
                new GuardrailContext(
                        GuardrailPhase.PRE_MODEL,
                        "agent",
                        "model",
                        new GuardrailPayload.ModelOutput(null),
                        Map.of());
        var decision = policy.evaluate(preModel).block();
        assertThat(decision).isNotNull();
        assertThat(decision.action()).isEqualTo(GuardrailDecision.Action.ALLOW);
        assertThat(decision.reason()).isEqualTo("phase skipped");
    }

    @Test
    void toolOutputWithPiiIsModified() {
        var ctx =
                new GuardrailContext(
                        GuardrailPhase.POST_TOOL,
                        "agent",
                        "tool",
                        new GuardrailPayload.ToolOutput(
                                "tool",
                                ToolResult.success(
                                        "use-1",
                                        "email alice@example.com please",
                                        Map.of("k", "v"))),
                        Map.of());
        var decision = policy.evaluate(ctx).block();
        assertThat(decision).isNotNull();
        assertThat(decision.action()).isEqualTo(GuardrailDecision.Action.MODIFY);
        var payload = (GuardrailPayload.ToolOutput) decision.modifiedPayload();
        assertThat(payload.result().content()).isEqualTo("email <redacted:email> please");
        assertThat(payload.result().toolUseId()).isEqualTo("use-1");
        assertThat(payload.result().metadata()).containsEntry("k", "v");
    }

    @Test
    void toolOutputWithoutPiiIsAllowed() {
        var ctx =
                new GuardrailContext(
                        GuardrailPhase.POST_TOOL,
                        "agent",
                        "tool",
                        new GuardrailPayload.ToolOutput(
                                "tool", ToolResult.success("use-2", "no sensitive data")),
                        Map.of());
        var decision = policy.evaluate(ctx).block();
        assertThat(decision).isNotNull();
        assertThat(decision.action()).isEqualTo(GuardrailDecision.Action.ALLOW);
        assertThat(decision.reason()).isEqualTo("no PII detected");
    }

    @Test
    void modelOutputTextThinkingAndToolUseAreRedacted() {
        var contents =
                List.<Content>of(
                        new Content.TextContent("reach me: bob@example.com"),
                        new Content.ThinkingContent("debug ssn 123-45-6789", 100, null),
                        // ToolUseContent args carry PII: the recipient email must be redacted
                        // before the response is logged / checkpointed / traced.
                        new Content.ToolUseContent(
                                "t1", "send_email", Map.of("to", "alice@example.com")));
        var response =
                new ModelResponse(
                        "resp-1",
                        contents,
                        new ModelResponse.Usage(0, 0, 0, 0),
                        ModelResponse.StopReason.END_TURN,
                        "claude");
        var ctx =
                new GuardrailContext(
                        GuardrailPhase.POST_MODEL,
                        "agent",
                        "claude",
                        new GuardrailPayload.ModelOutput(response),
                        Map.of());
        var decision = policy.evaluate(ctx).block();
        assertThat(decision).isNotNull();
        assertThat(decision.action()).isEqualTo(GuardrailDecision.Action.MODIFY);
        var payload = (GuardrailPayload.ModelOutput) decision.modifiedPayload();
        var redactedContents = payload.response().contents();
        assertThat(redactedContents).hasSize(3);
        assertThat(((Content.TextContent) redactedContents.get(0)).text())
                .isEqualTo("reach me: <redacted:email>");
        assertThat(((Content.ThinkingContent) redactedContents.get(1)).thinking())
                .isEqualTo("debug ssn <redacted:ssn>");
        var tu = (Content.ToolUseContent) redactedContents.get(2);
        assertThat(tu.toolId()).isEqualTo("t1");
        assertThat(tu.toolName()).isEqualTo("send_email");
        assertThat(tu.input()).containsEntry("to", "<redacted:email>");
    }

    @Test
    void toolUseContentWithoutPiiIsPassedThroughUnmodified() {
        // A ToolUseContent whose args carry no PII must NOT trigger a MODIFY decision when it is
        // the only content present — otherwise every tool call would needlessly rebuild the
        // ModelResponse. Identity check on the underlying record protects against accidental
        // copying.
        var tu = new Content.ToolUseContent("t1", "echo", Map.of("text", "hello"));
        var response =
                new ModelResponse(
                        "resp-1",
                        List.<Content>of(tu),
                        new ModelResponse.Usage(0, 0, 0, 0),
                        ModelResponse.StopReason.TOOL_USE,
                        "claude");
        var ctx =
                new GuardrailContext(
                        GuardrailPhase.POST_MODEL,
                        "agent",
                        "claude",
                        new GuardrailPayload.ModelOutput(response),
                        Map.of());
        var decision = policy.evaluate(ctx).block();
        assertThat(decision).isNotNull();
        assertThat(decision.action()).isEqualTo(GuardrailDecision.Action.ALLOW);
    }

    @Test
    void toolUseContentArgsAreRedactedDeeply() {
        // PII can hide inside nested Map / List structures (e.g. batch_send(to=[a, b, c]) or
        // create_user(profile={"email": ..., "ssn": ...})). The deep walk must reach all of them.
        var nested =
                Map.<String, Object>of(
                        "recipients", List.of("alice@example.com", "bob@example.com"),
                        "profile",
                                Map.of(
                                        "email", "carol@example.com",
                                        "ssn", "111-22-3333"),
                        "note", "no pii here");
        var tu = new Content.ToolUseContent("t-deep", "batch_send", nested);
        var response =
                new ModelResponse(
                        "resp-deep",
                        List.<Content>of(tu),
                        new ModelResponse.Usage(0, 0, 0, 0),
                        ModelResponse.StopReason.TOOL_USE,
                        "claude");
        var ctx =
                new GuardrailContext(
                        GuardrailPhase.POST_MODEL,
                        "agent",
                        "claude",
                        new GuardrailPayload.ModelOutput(response),
                        Map.of());
        var decision = policy.evaluate(ctx).block();
        assertThat(decision).isNotNull();
        assertThat(decision.action()).isEqualTo(GuardrailDecision.Action.MODIFY);
        var payload = (GuardrailPayload.ModelOutput) decision.modifiedPayload();
        var redactedTu = (Content.ToolUseContent) payload.response().contents().get(0);
        @SuppressWarnings("unchecked")
        List<String> recipients = (List<String>) redactedTu.input().get("recipients");
        assertThat(recipients).containsExactly("<redacted:email>", "<redacted:email>");
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) redactedTu.input().get("profile");
        assertThat(profile).containsEntry("email", "<redacted:email>");
        assertThat(profile).containsEntry("ssn", "<redacted:ssn>");
        assertThat(redactedTu.input()).containsEntry("note", "no pii here");
    }

    @Test
    void toolOutputOnlyFactorySkipsModelOutput() {
        var only = PiiRedactionPolicy.toolOutputOnly();
        var ctx =
                new GuardrailContext(
                        GuardrailPhase.POST_MODEL,
                        "agent",
                        "claude",
                        new GuardrailPayload.ModelOutput(null),
                        Map.of());
        var decision = only.evaluate(ctx).block();
        assertThat(decision).isNotNull();
        assertThat(decision.action()).isEqualTo(GuardrailDecision.Action.ALLOW);
        assertThat(decision.reason()).isEqualTo("phase skipped");
    }

    @Test
    void configOfSelectedPatternsOnlyRedactsThose() {
        var emailOnly = new PiiRedactionPolicy(PiiRedactionConfig.of(PiiPattern.EMAIL));
        var result = emailOnly.redactString("alice@example.com SSN 123-45-6789");
        assertThat(result.text()).isEqualTo("<redacted:email> SSN 123-45-6789");
        assertThat(result.matchCount()).isEqualTo(1);
    }

    @Test
    void policyExposesNameAndOrder() {
        assertThat(policy.name()).isEqualTo("pii-redaction");
        assertThat(policy.order()).isEqualTo(100);
        assertThat(new PiiRedactionPolicy(PiiRedactionConfig.defaults().withOrder(50)).order())
                .isEqualTo(50);
    }
}
