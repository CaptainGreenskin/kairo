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
package io.kairo.evolution.curator;

import io.kairo.api.evolution.EvolvedSkill;
import io.kairo.api.evolution.SkillTelemetry;

/**
 * Renders the umbrella-consolidation review prompt that an {@link LlmSkillCurator} implementation
 * should send to its model. The system prompt is a paraphrase of Hermes' {@code
 * CURATOR_REVIEW_PROMPT}; the user message is the catalog of candidate skills with their current
 * telemetry.
 *
 * <p>The output JSON contract the LLM is asked to follow:
 *
 * <pre>{@code
 * [
 *   {"action": "merge_into_umbrella", "umbrella": "pr-triage", "siblings": ["pr-fix-7"], "rationale": "..."},
 *   {"action": "create_umbrella", "umbrella": "release-prep", "siblings": ["…"], "skill_body": "…",
 *    "description": "…", "rationale": "…"},
 *   {"action": "demote", "umbrella": "deploys", "sibling": "gcp-quirks",
 *    "support_kind": "references", "file_name": "gcp-quirks.md", "body": "…", "rationale": "…"},
 *   {"action": "keep",    "umbrella": "agent-runtime",   "rationale": "…"},
 *   {"action": "archive", "umbrella": "stale-session-23", "rationale": "…"}
 * ]
 * }</pre>
 *
 * <p>The format is deliberately flat so callers can parse it with Jackson's generic {@code
 * TypeReference<List<Map<String,Object>>>} and feed into {@link CuratorActionParser}.
 */
public final class CuratorPromptBuilder {

    public static final String SYSTEM_PROMPT =
            "You are the Kairo skill CURATOR. This is an UMBRELLA-BUILDING consolidation pass,"
                    + " not a passive audit and not a duplicate-finder.\n\n"
                    + "The goal of the skill library is class-level instructions + experiential"
                    + " knowledge. A library of hundreds of narrow skills (each capturing one"
                    + " session's bug) is a FAILURE — not a feature. An agent searches by"
                    + " description; one broad umbrella with labeled subsections beats five narrow"
                    + " siblings for discoverability.\n\n"
                    + "HARD RULES:\n"
                    + "1. Never propose actions on pinned skills or skills marked BUNDLED/HUB/MANUAL"
                    + " in the provenance field.\n"
                    + "2. Archive is the MAX destructive action. Do NOT delete.\n"
                    + "3. Do NOT use use_count as the sole reason to consolidate or archive — counters"
                    + " are advisory (use=0 is absence of evidence, not evidence of absence).\n"
                    + "4. Pairwise distinctness is the WRONG bar. Ask: 'would a human maintainer write"
                    + " this as N separate skills, or as one skill with N labeled subsections?'\n\n"
                    + "Three consolidation paths — pick the right one per cluster:\n"
                    + "  a. merge_into_umbrella — one cluster member is already broad enough to be"
                    + " the umbrella; absorb sibling sections into its SKILL.md, then archive each"
                    + " sibling.\n"
                    + "  b. create_umbrella — no member is broad enough; create a new umbrella"
                    + " SKILL.md, then archive each sibling.\n"
                    + "  c. demote — a sibling has narrow-but-valuable session-specific content; move"
                    + " it under the umbrella as references/<topic>.md, templates/<name>.<ext>, or"
                    + " scripts/<name>.<ext>, then archive the sibling.\n\n"
                    + "Respond with a strict JSON array. Each element must include the exact shape"
                    + " listed in the user prompt for the action you chose. No prose outside the JSON."
                    + " If you have nothing to do, respond with [].";

    private CuratorPromptBuilder() {}

    /** Render the user-message payload (the actual candidate catalog) for the model. */
    public static String renderUserPrompt(SkillCatalog catalog) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Candidates (agent-created, eligible)\n\n");
        if (catalog.candidates().isEmpty()) {
            sb.append("(none)\n\n");
        } else {
            for (SkillCatalog.Entry e : catalog.candidates()) {
                appendEntry(sb, e);
            }
        }
        if (!catalog.immune().isEmpty()) {
            sb.append("# Immune (DO NOT TOUCH — pinned / bundled / hub / manual)\n\n");
            for (SkillCatalog.Entry e : catalog.immune()) {
                appendEntry(sb, e);
            }
        }
        sb.append("\n# Output\n\n")
                .append(
                        "Return a JSON array of actions as documented in the system prompt. "
                                + "Use empty array [] if no changes are needed.");
        return sb.toString();
    }

    private static void appendEntry(StringBuilder sb, SkillCatalog.Entry e) {
        EvolvedSkill s = e.skill();
        SkillTelemetry t = e.telemetry();
        sb.append("## ").append(s.name()).append('\n');
        sb.append("- description: ").append(safe(s.description())).append('\n');
        sb.append("- category: ").append(safe(s.category())).append('\n');
        sb.append("- tags: ").append(String.join(", ", s.tags())).append('\n');
        if (t != null) {
            sb.append("- state: ").append(t.state()).append('\n');
            sb.append("- provenance: ").append(t.provenance()).append('\n');
            sb.append("- pinned: ").append(t.pinned()).append('\n');
            sb.append("- use_count: ").append(t.useCount()).append('\n');
            sb.append("- view_count: ").append(t.viewCount()).append('\n');
            sb.append("- patch_count: ").append(t.patchCount()).append('\n');
            if (t.lastActivityAt() != null) {
                sb.append("- last_activity_at: ").append(t.lastActivityAt()).append('\n');
            }
        } else {
            sb.append("- telemetry: <none yet>\n");
        }
        sb.append("- instructions_preview: ").append(preview(s.instructions(), 240)).append("\n\n");
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace('\n', ' ').trim();
    }

    private static String preview(String s, int max) {
        if (s == null) return "";
        String flat = s.replace('\n', ' ').trim();
        return flat.length() > max ? flat.substring(0, max) + "…" : flat;
    }
}
