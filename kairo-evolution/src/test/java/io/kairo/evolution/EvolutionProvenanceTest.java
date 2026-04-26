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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EvolutionProvenanceTest {

    private EvolutionProvenance full() {
        return new EvolutionProvenance(
                "sess-001", "pattern-detected", "claude-3-5-sonnet", "abc123", "PASS", "v1.0");
    }

    private EvolutionProvenance noScan() {
        return new EvolutionProvenance(
                "sess-001", "pattern-detected", "claude-3-5-sonnet", "abc123", null, null);
    }

    @Test
    void accessors() {
        EvolutionProvenance p = full();
        assertThat(p.sourceSessionId()).isEqualTo("sess-001");
        assertThat(p.triggerReason()).isEqualTo("pattern-detected");
        assertThat(p.reviewModel()).isEqualTo("claude-3-5-sonnet");
        assertThat(p.contentHash()).isEqualTo("abc123");
        assertThat(p.scanVerdict()).isEqualTo("PASS");
        assertThat(p.scanVersion()).isEqualTo("v1.0");
    }

    @Test
    void toMetadata_containsAllCoreFields() {
        Map<String, String> meta = full().toMetadata();
        assertThat(meta)
                .containsEntry("sourceSessionId", "sess-001")
                .containsEntry("triggerReason", "pattern-detected")
                .containsEntry("reviewModel", "claude-3-5-sonnet")
                .containsEntry("contentHash", "abc123");
    }

    @Test
    void toMetadata_includesScanFieldsWhenPresent() {
        Map<String, String> meta = full().toMetadata();
        assertThat(meta).containsEntry("scanVerdict", "PASS").containsEntry("scanVersion", "v1.0");
    }

    @Test
    void toMetadata_omitsScanFieldsWhenNull() {
        Map<String, String> meta = noScan().toMetadata();
        assertThat(meta).doesNotContainKey("scanVerdict").doesNotContainKey("scanVersion");
    }

    @Test
    void toMetadata_returnsUnmodifiableMap() {
        Map<String, String> meta = full().toMetadata();
        assertThatThrownBy(() -> meta.put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void toMetadata_hasFourEntriesWithoutScan() {
        assertThat(noScan().toMetadata()).hasSize(4);
    }

    @Test
    void toMetadata_hasSixEntriesWithScan() {
        assertThat(full().toMetadata()).hasSize(6);
    }

    @Test
    void equalProvenancesAreEqual() {
        assertThat(full()).isEqualTo(full());
    }

    @Test
    void hashCodeConsistentWithEquals() {
        assertThat(full().hashCode()).isEqualTo(full().hashCode());
    }

    @Test
    void toStringContainsSourceSessionId() {
        assertThat(full().toString()).contains("sess-001");
    }
}
