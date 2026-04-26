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
package io.kairo.security.compliance;

import io.kairo.api.event.KairoEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Subscribes to {@link KairoEvent}s on the security and execution domains and aggregates them into
 * per-run compliance counters that {@link #snapshot()} renders as a {@link ComplianceReport}.
 *
 * <p>Wire it as a bus subscriber:
 *
 * <pre>{@code
 * ComplianceReportCollector collector = new ComplianceReportCollector();
 * eventBus.subscribe(collector);
 * // ... agent run ...
 * String md = collector.snapshot().renderMarkdown();
 * }</pre>
 *
 * <p>This is not a new SPI — it implements {@link Consumer Consumer&lt;KairoEvent&gt;}, the
 * standard subscriber shape on {@code KairoEventBus}. Hosting apps that need a different
 * aggregation strategy can substitute their own {@code Consumer<KairoEvent>}.
 *
 * <p>Thread-safe: counters use {@link AtomicLong} and per-key counters live in {@link
 * ConcurrentHashMap}, so concurrent bus dispatch from reactive pipelines is safe.
 *
 * @since v1.0.0
 */
public final class ComplianceReportCollector implements Consumer<KairoEvent> {

    private final Instant runStart = Instant.now();

    private final AtomicLong guardrailAllow = new AtomicLong();
    private final AtomicLong guardrailDeny = new AtomicLong();
    private final AtomicLong guardrailModify = new AtomicLong();
    private final AtomicLong guardrailWarn = new AtomicLong();
    private final AtomicLong mcpBlock = new AtomicLong();
    private final AtomicLong piiRedactions = new AtomicLong();

    private final ConcurrentHashMap<String, AtomicLong> toolInvocations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> guardrailByPolicy =
            new ConcurrentHashMap<>();

    private static final String PII_POLICY_NAME = "pii-redaction";

    @Override
    public void accept(KairoEvent event) {
        if (event == null) {
            return;
        }
        switch (event.domain()) {
            case KairoEvent.DOMAIN_SECURITY -> recordSecurity(event);
            case KairoEvent.DOMAIN_EXECUTION -> recordExecution(event);
            default -> {
                // other domains (evolution, team) are not part of the compliance report v1.0
            }
        }
    }

    private void recordSecurity(KairoEvent event) {
        String type = event.eventType();
        switch (type) {
            case "GUARDRAIL_ALLOW" -> guardrailAllow.incrementAndGet();
            case "GUARDRAIL_DENY" -> guardrailDeny.incrementAndGet();
            case "GUARDRAIL_MODIFY" -> guardrailModify.incrementAndGet();
            case "GUARDRAIL_WARN" -> guardrailWarn.incrementAndGet();
            case "MCP_BLOCK" -> mcpBlock.incrementAndGet();
            default -> {
                // ignore: forward-compatible with future event types
            }
        }
        Object policyAttr = event.attributes().get("policyName");
        if (policyAttr instanceof String policyName && !policyName.isBlank()) {
            guardrailByPolicy.computeIfAbsent(policyName, k -> new AtomicLong()).incrementAndGet();
            if ("GUARDRAIL_MODIFY".equals(type) && PII_POLICY_NAME.equals(policyName)) {
                piiRedactions.incrementAndGet();
            }
        }
    }

    private void recordExecution(KairoEvent event) {
        // Tool invocations carry attribute "toolName" — see kairo-core
        // EvolutionPipelineOrchestrator
        // and ReActLoop publishers.
        Object toolAttr = event.attributes().get("toolName");
        if (toolAttr instanceof String toolName && !toolName.isBlank()) {
            toolInvocations.computeIfAbsent(toolName, k -> new AtomicLong()).incrementAndGet();
        }
    }

    /** Returns an immutable snapshot of the counters at the moment of invocation. */
    public ComplianceReport snapshot() {
        return new ComplianceReport(
                runStart,
                Instant.now(),
                guardrailAllow.get(),
                guardrailDeny.get(),
                guardrailModify.get(),
                guardrailWarn.get(),
                mcpBlock.get(),
                piiRedactions.get(),
                freeze(toolInvocations),
                freeze(guardrailByPolicy));
    }

    private static Map<String, Long> freeze(ConcurrentHashMap<String, AtomicLong> source) {
        Map<String, Long> out = new LinkedHashMap<>(source.size());
        source.forEach((k, v) -> out.put(k, v.get()));
        return out;
    }
}
