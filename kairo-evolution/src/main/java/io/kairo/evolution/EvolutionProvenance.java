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
package io.kairo.evolution;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Internal value object capturing provenance metadata for an evolved skill.
 *
 * <p>Records the origin session, trigger reason, review model used, and content hash for
 * auditability. Not part of the public kairo-api contract.
 *
 * @param sourceSessionId the session where evolution was triggered
 * @param triggerReason why the evolution was triggered
 * @param reviewModel the model used for the review call
 * @param contentHash SHA-256 hash of the skill instructions content
 * @param scanVerdict the scan result (PASS/REJECT), nullable if not yet scanned
 * @param scanVersion the version of the scanner used, nullable
 * @since v0.9 (Experimental)
 */
public record EvolutionProvenance(
        String sourceSessionId,
        String triggerReason,
        String reviewModel,
        String contentHash,
        @Nullable String scanVerdict,
        @Nullable String scanVersion) {

    /**
     * Convert this provenance into a flat metadata map suitable for {@link
     * io.kairo.api.evolution.EvolvedSkill#metadata()}.
     *
     * @return an unmodifiable map of metadata entries
     */
    public Map<String, String> toMetadata() {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("sourceSessionId", sourceSessionId);
        meta.put("triggerReason", triggerReason);
        meta.put("reviewModel", reviewModel);
        meta.put("contentHash", contentHash);
        if (scanVerdict != null) meta.put("scanVerdict", scanVerdict);
        if (scanVersion != null) meta.put("scanVersion", scanVersion);
        return Map.copyOf(meta);
    }
}
