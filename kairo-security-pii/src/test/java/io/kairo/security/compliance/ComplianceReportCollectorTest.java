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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.event.KairoEvent;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComplianceReportCollectorTest {

    @Test
    void securityCountersIncrementByEventType() {
        var c = new ComplianceReportCollector();
        c.accept(KairoEvent.of(KairoEvent.DOMAIN_SECURITY, "GUARDRAIL_ALLOW", Map.of()));
        c.accept(KairoEvent.of(KairoEvent.DOMAIN_SECURITY, "GUARDRAIL_DENY", Map.of()));
        c.accept(KairoEvent.of(KairoEvent.DOMAIN_SECURITY, "GUARDRAIL_MODIFY", Map.of()));
        c.accept(KairoEvent.of(KairoEvent.DOMAIN_SECURITY, "GUARDRAIL_WARN", Map.of()));
        c.accept(KairoEvent.of(KairoEvent.DOMAIN_SECURITY, "MCP_BLOCK", Map.of()));
        var r = c.snapshot();
        assertThat(r.guardrailAllow()).isEqualTo(1);
        assertThat(r.guardrailDeny()).isEqualTo(1);
        assertThat(r.guardrailModify()).isEqualTo(1);
        assertThat(r.guardrailWarn()).isEqualTo(1);
        assertThat(r.mcpBlock()).isEqualTo(1);
    }

    @Test
    void piiRedactionIsCountedSeparately() {
        var c = new ComplianceReportCollector();
        c.accept(
                KairoEvent.of(
                        KairoEvent.DOMAIN_SECURITY,
                        "GUARDRAIL_MODIFY",
                        Map.of("policyName", "pii-redaction")));
        c.accept(
                KairoEvent.of(
                        KairoEvent.DOMAIN_SECURITY,
                        "GUARDRAIL_MODIFY",
                        Map.of("policyName", "deny-list")));
        var r = c.snapshot();
        assertThat(r.guardrailModify()).isEqualTo(2);
        assertThat(r.piiRedactions()).isEqualTo(1);
        assertThat(r.guardrailByPolicy())
                .containsEntry("pii-redaction", 1L)
                .containsEntry("deny-list", 1L);
    }

    @Test
    void toolInvocationsAreAggregatedFromExecutionDomain() {
        var c = new ComplianceReportCollector();
        c.accept(
                KairoEvent.of(
                        KairoEvent.DOMAIN_EXECUTION, "TOOL_CALLED", Map.of("toolName", "search")));
        c.accept(
                KairoEvent.of(
                        KairoEvent.DOMAIN_EXECUTION, "TOOL_CALLED", Map.of("toolName", "search")));
        c.accept(
                KairoEvent.of(
                        KairoEvent.DOMAIN_EXECUTION, "TOOL_CALLED", Map.of("toolName", "fetch")));
        var r = c.snapshot();
        assertThat(r.toolInvocations()).containsEntry("search", 2L).containsEntry("fetch", 1L);
    }

    @Test
    void unknownDomainsAreIgnored() {
        var c = new ComplianceReportCollector();
        c.accept(KairoEvent.of(KairoEvent.DOMAIN_EVOLUTION, "ANY", Map.of()));
        c.accept(KairoEvent.of(KairoEvent.DOMAIN_TEAM, "ANY", Map.of()));
        c.accept(null);
        var r = c.snapshot();
        assertThat(r.guardrailAllow() + r.guardrailDeny() + r.guardrailModify()).isZero();
    }

    @Test
    void renderMarkdownContainsAllSections() {
        var c = new ComplianceReportCollector();
        c.accept(
                KairoEvent.of(
                        KairoEvent.DOMAIN_SECURITY,
                        "GUARDRAIL_DENY",
                        Map.of("policyName", "deny-list")));
        c.accept(
                KairoEvent.of(
                        KairoEvent.DOMAIN_EXECUTION, "TOOL_CALLED", Map.of("toolName", "search")));
        String md = c.snapshot().renderMarkdown();
        assertThat(md)
                .contains("# Kairo Compliance Report")
                .contains("## Guardrail decisions")
                .contains("## PII redactions")
                .contains("## Tool invocations")
                .contains("## Guardrail decisions by policy")
                .contains("| DENY   | 1 |")
                .contains("| search | 1 |")
                .contains("| deny-list | 1 |");
    }

    @Test
    void emptyReportRendersGracefully() {
        var c = new ComplianceReportCollector();
        String md = c.snapshot().renderMarkdown();
        assertThat(md)
                .contains("_No tool invocations recorded._")
                .contains("_No policy decisions recorded._");
    }
}
