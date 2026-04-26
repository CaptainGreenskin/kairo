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

import io.kairo.api.guardrail.GuardrailContext;
import io.kairo.api.guardrail.GuardrailDecision;
import io.kairo.api.guardrail.GuardrailPayload;
import io.kairo.api.guardrail.GuardrailPolicy;
import io.kairo.api.message.Content;
import io.kairo.api.model.ModelResponse;
import io.kairo.api.tool.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import reactor.core.publisher.Mono;

/**
 * A stock {@link GuardrailPolicy} that redacts PII from payloads using configured regex patterns.
 *
 * <p>This is not a new SPI — it is an implementation of the existing {@link GuardrailPolicy}
 * contract. Register it as a Spring bean or add it directly to a guardrail chain.
 *
 * <p>Evaluates only at phases declared by {@link PiiRedactionConfig#phases()}. At any other phase
 * the policy returns {@link GuardrailDecision#allow(String, String)} without inspecting the
 * payload.
 *
 * <p>Redaction preserves the payload shape: {@link GuardrailPayload.ToolOutput} is rewritten with a
 * new {@link ToolResult}; {@link GuardrailPayload.ModelOutput} is rewritten with a new {@link
 * ModelResponse} whose text/thinking blocks have matches replaced in-place.
 */
public final class PiiRedactionPolicy implements GuardrailPolicy {

    private final PiiRedactionConfig config;

    public PiiRedactionPolicy(PiiRedactionConfig config) {
        this.config = config;
    }

    /** Convenience ctor using {@link PiiRedactionConfig#defaults()}. */
    public PiiRedactionPolicy() {
        this(PiiRedactionConfig.defaults());
    }

    @Override
    public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
        if (!config.phases().contains(context.phase())) {
            return Mono.just(GuardrailDecision.allow("phase skipped", name()));
        }
        GuardrailPayload payload = context.payload();
        if (payload instanceof GuardrailPayload.ToolOutput out) {
            return Mono.just(redactToolOutput(out));
        }
        if (payload instanceof GuardrailPayload.ModelOutput out) {
            return Mono.just(redactModelOutput(out));
        }
        return Mono.just(GuardrailDecision.allow("payload not redactable", name()));
    }

    @Override
    public int order() {
        return config.order();
    }

    @Override
    public String name() {
        return "pii-redaction";
    }

    private GuardrailDecision redactToolOutput(GuardrailPayload.ToolOutput out) {
        ToolResult result = out.result();
        if (result == null || result.content() == null) {
            return GuardrailDecision.allow("nothing to redact", name());
        }
        RedactionResult red = redactString(result.content());
        if (red.matchCount() == 0) {
            return GuardrailDecision.allow("no PII detected", name());
        }
        ToolResult redacted =
                new ToolResult(result.toolUseId(), red.text(), result.isError(), result.metadata());
        return GuardrailDecision.modify(
                new GuardrailPayload.ToolOutput(out.toolName(), redacted),
                "redacted " + red.matchCount() + " PII match(es) in tool output",
                name());
    }

    private GuardrailDecision redactModelOutput(GuardrailPayload.ModelOutput out) {
        ModelResponse response = out.response();
        if (response == null) {
            return GuardrailDecision.allow("nothing to redact", name());
        }
        List<Content> rewritten = new ArrayList<>(response.contents().size());
        int totalMatches = 0;
        for (Content c : response.contents()) {
            if (c instanceof Content.TextContent tc) {
                RedactionResult red = redactString(tc.text());
                totalMatches += red.matchCount();
                rewritten.add(red.matchCount() == 0 ? tc : new Content.TextContent(red.text()));
            } else if (c instanceof Content.ThinkingContent tc) {
                RedactionResult red = redactString(tc.thinking());
                totalMatches += red.matchCount();
                rewritten.add(
                        red.matchCount() == 0
                                ? tc
                                : new Content.ThinkingContent(red.text(), tc.budgetTokens()));
            } else {
                rewritten.add(c);
            }
        }
        if (totalMatches == 0) {
            return GuardrailDecision.allow("no PII detected", name());
        }
        ModelResponse redacted =
                new ModelResponse(
                        response.id(),
                        rewritten,
                        response.usage(),
                        response.stopReason(),
                        response.model());
        return GuardrailDecision.modify(
                new GuardrailPayload.ModelOutput(redacted),
                "redacted " + totalMatches + " PII match(es) in model output",
                name());
    }

    /**
     * Apply the configured patterns to an input string.
     *
     * <p>Visibility is package-private so tests can exercise it without constructing a full
     * guardrail payload.
     */
    RedactionResult redactString(String input) {
        if (input == null || input.isEmpty()) {
            return new RedactionResult(input, 0);
        }
        String current = input;
        int matches = 0;
        for (Map.Entry<Pattern, String> entry : config.patterns().entrySet()) {
            Matcher m = entry.getKey().matcher(current);
            StringBuilder sb = new StringBuilder();
            int localMatches = 0;
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(entry.getValue()));
                localMatches++;
            }
            if (localMatches > 0) {
                m.appendTail(sb);
                current = sb.toString();
                matches += localMatches;
            }
        }
        return new RedactionResult(current, matches);
    }

    /**
     * Internal container for the redacted text plus the number of matches replaced.
     *
     * @param text the redacted text (equal to input if no matches)
     * @param matchCount total PII matches replaced across all configured patterns
     */
    record RedactionResult(String text, int matchCount) {}

    /** Public accessor so hosting apps can introspect which phases + patterns are active. */
    public PiiRedactionConfig config() {
        return config;
    }

    /** Factory: policy that redacts only tool output (useful when model output is trusted). */
    public static PiiRedactionPolicy toolOutputOnly() {
        return new PiiRedactionPolicy(
                PiiRedactionConfig.defaults()
                        .withPhases(io.kairo.api.guardrail.GuardrailPhase.POST_TOOL));
    }

    /** Factory: policy seeded from the stock {@link PiiPattern} set with defaults. */
    public static PiiRedactionPolicy stock() {
        return new PiiRedactionPolicy(PiiRedactionConfig.defaults());
    }
}
