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
package io.kairo.api.evolution;

import io.kairo.api.Experimental;
import java.time.Instant;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Sidecar telemetry + governance record for an {@link EvolvedSkill}. Persisted alongside the skill
 * (separate file in {@link SkillTelemetryStore}) to keep skill content stable while the curator
 * mutates counters and lifecycle state.
 *
 * <p>The curator decides {@link SkillLifecycleState} transitions from the {@code last_*_at}
 * timestamps; counters are advisory and never the sole basis for archival (per Hermes' rule: "use=0
 * is not evidence a skill is valuable; it's absence of evidence either way").
 *
 * @param skillName the {@link EvolvedSkill#name()} this telemetry tracks
 * @param state lifecycle state — drives discovery and auto-transitions
 * @param provenance origin marker — curator only acts on {@link SkillProvenance#AGENT_CREATED}
 * @param pinned opt-out from all auto-transitions and consolidation actions
 * @param useCount times the skill was injected/applied
 * @param viewCount times the skill was inspected (read but not applied)
 * @param patchCount times the skill body or metadata was patched
 * @param lastUsedAt timestamp of the most recent use, null if never
 * @param lastViewedAt timestamp of the most recent view, null if never
 * @param lastPatchedAt timestamp of the most recent patch, null if never
 * @param createdAt when the telemetry record was first created
 * @param archivedAt when the skill transitioned to {@link SkillLifecycleState#ARCHIVED}, null if
 *     never
 * @param absorbedInto if archived as part of an umbrella consolidation, the umbrella skill's name;
 *     null if archived for other reasons
 * @since v0.10 (Experimental)
 */
@Experimental("Self-Evolution SPI — contract may change before v1.2.0 stabilization")
public record SkillTelemetry(
        String skillName,
        SkillLifecycleState state,
        SkillProvenance provenance,
        boolean pinned,
        long useCount,
        long viewCount,
        long patchCount,
        @Nullable Instant lastUsedAt,
        @Nullable Instant lastViewedAt,
        @Nullable Instant lastPatchedAt,
        Instant createdAt,
        @Nullable Instant archivedAt,
        @Nullable String absorbedInto) {

    public SkillTelemetry {
        Objects.requireNonNull(skillName, "skillName must not be null");
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(provenance, "provenance must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /** Fresh telemetry record for a newly-created skill, with all counters at 0. */
    public static SkillTelemetry initial(
            String skillName, SkillProvenance provenance, Instant now) {
        return new SkillTelemetry(
                skillName,
                SkillLifecycleState.ACTIVE,
                provenance,
                false,
                0L,
                0L,
                0L,
                null,
                null,
                null,
                now,
                null,
                null);
    }

    /** The newest activity timestamp across use/view/patch, or null if never active. */
    @Nullable
    public Instant lastActivityAt() {
        Instant latest = null;
        if (lastUsedAt != null) latest = lastUsedAt;
        if (lastViewedAt != null && (latest == null || lastViewedAt.isAfter(latest))) {
            latest = lastViewedAt;
        }
        if (lastPatchedAt != null && (latest == null || lastPatchedAt.isAfter(latest))) {
            latest = lastPatchedAt;
        }
        return latest;
    }

    /** Sum of all observed activity counters (use + view + patch). */
    public long activityCount() {
        return useCount + viewCount + patchCount;
    }

    /** True if pinned, bundled, hub-installed, or manual — i.e. off-limits to the curator. */
    public boolean isCuratorImmune() {
        return pinned || provenance != SkillProvenance.AGENT_CREATED;
    }

    // ----- Mutators (record-style: return a new instance) -----

    public SkillTelemetry withUse(Instant at) {
        return new SkillTelemetry(
                skillName,
                state,
                provenance,
                pinned,
                useCount + 1,
                viewCount,
                patchCount,
                at,
                lastViewedAt,
                lastPatchedAt,
                createdAt,
                archivedAt,
                absorbedInto);
    }

    public SkillTelemetry withView(Instant at) {
        return new SkillTelemetry(
                skillName,
                state,
                provenance,
                pinned,
                useCount,
                viewCount + 1,
                patchCount,
                lastUsedAt,
                at,
                lastPatchedAt,
                createdAt,
                archivedAt,
                absorbedInto);
    }

    public SkillTelemetry withPatch(Instant at) {
        return new SkillTelemetry(
                skillName,
                state,
                provenance,
                pinned,
                useCount,
                viewCount,
                patchCount + 1,
                lastUsedAt,
                lastViewedAt,
                at,
                createdAt,
                archivedAt,
                absorbedInto);
    }

    public SkillTelemetry withState(SkillLifecycleState newState) {
        return new SkillTelemetry(
                skillName,
                newState,
                provenance,
                pinned,
                useCount,
                viewCount,
                patchCount,
                lastUsedAt,
                lastViewedAt,
                lastPatchedAt,
                createdAt,
                archivedAt,
                absorbedInto);
    }

    public SkillTelemetry withPinned(boolean newPinned) {
        return new SkillTelemetry(
                skillName,
                state,
                provenance,
                newPinned,
                useCount,
                viewCount,
                patchCount,
                lastUsedAt,
                lastViewedAt,
                lastPatchedAt,
                createdAt,
                archivedAt,
                absorbedInto);
    }

    public SkillTelemetry archived(Instant at, @Nullable String absorbedInto) {
        return new SkillTelemetry(
                skillName,
                SkillLifecycleState.ARCHIVED,
                provenance,
                pinned,
                useCount,
                viewCount,
                patchCount,
                lastUsedAt,
                lastViewedAt,
                lastPatchedAt,
                createdAt,
                at,
                absorbedInto);
    }
}
