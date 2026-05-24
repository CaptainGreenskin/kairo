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

import java.util.List;

/**
 * A single recommendation produced by the LLM curator for the {@code UmbrellaConsolidationPlanner}.
 * Mirrors the three-path consolidation strategy verbatim from Hermes' {@code CURATOR_REVIEW_PROMPT}
 * (curator.py lines 332-410):
 *
 * <ul>
 *   <li>{@link MergeIntoUmbrella} — an existing skill is already broad enough; absorb siblings
 *       (described in {@link MergeIntoUmbrella#sibling} fragments) and then archive each sibling.
 *   <li>{@link CreateUmbrella} — no member is broad enough; write a new umbrella SKILL.md whose
 *       body covers the shared workflow, then archive the narrow siblings.
 *   <li>{@link DemoteToSupport} — narrow but valuable session-specific content; move it into the
 *       umbrella's {@code references/}, {@code templates/}, or {@code scripts/} subtree, then
 *       archive the sibling.
 *   <li>{@link Keep} — leave the skill alone (used when the skill is already a class-level
 *       umbrella).
 *   <li>{@link Archive} — straight archive without merge (e.g. obvious junk; Hermes calls this
 *       "pruning with no forwarding target").
 * </ul>
 *
 * <p>Sibling-skill names referenced by an action must exist in the catalog at action time, or the
 * executor will skip the action and log a warning.
 */
public sealed interface CuratorAction {

    /** The umbrella skill that this action centers on (target of merge/create/demote/keep). */
    String umbrella();

    /**
     * Merge each sibling's unique insight as a labeled section into the existing umbrella SKILL.md
     * body, then archive each sibling (recording {@code absorbedInto=umbrella} in telemetry).
     *
     * @param umbrella name of the skill that absorbs the siblings
     * @param siblings other skill names to be folded in
     * @param rationale human-readable justification (LLM's own words)
     */
    record MergeIntoUmbrella(String umbrella, List<String> siblings, String rationale)
            implements CuratorAction {
        public MergeIntoUmbrella {
            siblings = List.copyOf(siblings);
        }
    }

    /**
     * Create a brand-new umbrella SKILL.md whose body covers the shared workflow. After creation,
     * archive each sibling with {@code absorbedInto=umbrella}.
     *
     * @param umbrella name of the new umbrella skill to create
     * @param siblings narrow skills to fold under it
     * @param skillBody the SKILL.md body the LLM proposes for the new umbrella
     * @param description short description for the new umbrella's frontmatter
     * @param rationale human-readable justification
     */
    record CreateUmbrella(
            String umbrella,
            List<String> siblings,
            String skillBody,
            String description,
            String rationale)
            implements CuratorAction {
        public CreateUmbrella {
            siblings = List.copyOf(siblings);
        }
    }

    /**
     * Move {@code sibling}'s body into {@code umbrella}'s {@code references/<topic>.md} (or
     * templates/scripts variant) and archive the sibling. The executor decides the subdirectory
     * based on {@code supportKind}.
     *
     * @param umbrella the umbrella skill that gains the support file
     * @param sibling the narrow skill being demoted
     * @param supportKind which subdirectory: {@link SupportKind#REFERENCES}, {@link
     *     SupportKind#TEMPLATES}, or {@link SupportKind#SCRIPTS}
     * @param fileName the file name under the chosen subdirectory (e.g. {@code aws-quirks.md})
     * @param body content to write into that file
     * @param rationale human-readable justification
     */
    record DemoteToSupport(
            String umbrella,
            String sibling,
            SupportKind supportKind,
            String fileName,
            String body,
            String rationale)
            implements CuratorAction {}

    /** Leave the skill alone — the LLM verifies it is already a class-level umbrella. */
    record Keep(String umbrella, String rationale) implements CuratorAction {}

    /**
     * Archive the skill without merging (e.g. obvious one-session debug-note that should be
     * recoverable but not living in the main library).
     */
    record Archive(String umbrella, String rationale) implements CuratorAction {}

    /** Subdirectory under the umbrella that absorbs the demoted sibling's content. */
    enum SupportKind {
        REFERENCES,
        TEMPLATES,
        SCRIPTS
    }
}
