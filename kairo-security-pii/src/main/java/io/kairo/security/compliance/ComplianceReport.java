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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable snapshot of {@link ComplianceReportCollector} state at a point in time.
 *
 * <p>Suitable for GDPR/HIPAA-style audit-trail evidence. The Markdown renderer ({@link
 * #renderMarkdown()}) emits a single page summarizing per-run security posture: guardrail decisions
 * broken down by action, PII redactions, tool invocations, and the time window covered by the
 * report.
 *
 * @param runStart timestamp of the first event observed (or collector construction)
 * @param snapshotAt timestamp this snapshot was taken
 * @param guardrailAllow count of {@code GUARDRAIL_ALLOW} events
 * @param guardrailDeny count of {@code GUARDRAIL_DENY} events
 * @param guardrailModify count of {@code GUARDRAIL_MODIFY} events
 * @param guardrailWarn count of {@code GUARDRAIL_WARN} events
 * @param mcpBlock count of {@code MCP_BLOCK} events
 * @param piiRedactions count of {@code GUARDRAIL_MODIFY} events whose {@code policy_name} is {@code
 *     pii-redaction}
 * @param toolInvocations per-tool execution counts (key = tool name)
 * @param guardrailByPolicy per-policy decision counts (key = policy name)
 * @since v1.0.0
 */
public record ComplianceReport(
        Instant runStart,
        Instant snapshotAt,
        long guardrailAllow,
        long guardrailDeny,
        long guardrailModify,
        long guardrailWarn,
        long mcpBlock,
        long piiRedactions,
        Map<String, Long> toolInvocations,
        Map<String, Long> guardrailByPolicy) {

    public ComplianceReport {
        toolInvocations = Map.copyOf(toolInvocations == null ? Map.of() : toolInvocations);
        guardrailByPolicy = Map.copyOf(guardrailByPolicy == null ? Map.of() : guardrailByPolicy);
    }

    /**
     * Render this report as a single-page Markdown document.
     *
     * <p>Output is stable enough to commit to a compliance-evidence bundle: every count is named,
     * totals are explicit, and the time window is in ISO-8601 UTC.
     */
    public String renderMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Kairo Compliance Report\n\n");
        sb.append("- **Run start**: ").append(runStart).append('\n');
        sb.append("- **Snapshot at**: ").append(snapshotAt).append('\n');
        sb.append("- **Window duration**: ")
                .append(java.time.Duration.between(runStart, snapshotAt))
                .append("\n\n");

        sb.append("## Guardrail decisions\n\n");
        sb.append("| Action | Count |\n");
        sb.append("|--------|-------|\n");
        sb.append("| ALLOW  | ").append(guardrailAllow).append(" |\n");
        sb.append("| DENY   | ").append(guardrailDeny).append(" |\n");
        sb.append("| MODIFY | ").append(guardrailModify).append(" |\n");
        sb.append("| WARN   | ").append(guardrailWarn).append(" |\n");
        sb.append("| MCP_BLOCK | ").append(mcpBlock).append(" |\n\n");

        sb.append("## PII redactions\n\n");
        sb.append("- Total redaction events: ").append(piiRedactions).append("\n\n");

        sb.append("## Tool invocations\n\n");
        if (toolInvocations.isEmpty()) {
            sb.append("_No tool invocations recorded._\n\n");
        } else {
            sb.append("| Tool | Count |\n");
            sb.append("|------|-------|\n");
            new LinkedHashMap<>(toolInvocations)
                    .forEach(
                            (tool, count) ->
                                    sb.append("| ")
                                            .append(tool)
                                            .append(" | ")
                                            .append(count)
                                            .append(" |\n"));
            sb.append('\n');
        }

        sb.append("## Guardrail decisions by policy\n\n");
        if (guardrailByPolicy.isEmpty()) {
            sb.append("_No policy decisions recorded._\n");
        } else {
            sb.append("| Policy | Count |\n");
            sb.append("|--------|-------|\n");
            new LinkedHashMap<>(guardrailByPolicy)
                    .forEach(
                            (policy, count) ->
                                    sb.append("| ")
                                            .append(policy)
                                            .append(" | ")
                                            .append(count)
                                            .append(" |\n"));
        }
        return sb.toString();
    }
}
