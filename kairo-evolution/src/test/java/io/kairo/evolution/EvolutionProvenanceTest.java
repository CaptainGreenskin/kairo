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

import java.util.Map;
import org.junit.jupiter.api.Test;

class EvolutionProvenanceTest {

    private static EvolutionProvenance provenance() {
        return new EvolutionProvenance(
                "session-1", "high-error-rate", "claude-3", "abc123", null, null);
    }

    @Test
    void sourceSessionId() {
        assertThat(provenance().sourceSessionId()).isEqualTo("session-1");
    }

    @Test
    void triggerReason() {
        assertThat(provenance().triggerReason()).isEqualTo("high-error-rate");
    }

    @Test
    void reviewModel() {
        assertThat(provenance().reviewModel()).isEqualTo("claude-3");
    }

    @Test
    void contentHash() {
        assertThat(provenance().contentHash()).isEqualTo("abc123");
    }

    @Test
    void scanVerdictNullable() {
        assertThat(provenance().scanVerdict()).isNull();
    }

    @Test
    void toMetadataContainsCoreFields() {
        Map<String, String> meta = provenance().toMetadata();
        assertThat(meta).containsKey("sourceSessionId");
        assertThat(meta).containsKey("triggerReason");
        assertThat(meta).containsKey("reviewModel");
        assertThat(meta).containsKey("contentHash");
    }

    @Test
    void toMetadataExcludesNullScanFields() {
        Map<String, String> meta = provenance().toMetadata();
        assertThat(meta).doesNotContainKey("scanVerdict");
        assertThat(meta).doesNotContainKey("scanVersion");
    }

    @Test
    void toMetadataIncludesScanFieldsWhenPresent() {
        EvolutionProvenance p =
                new EvolutionProvenance("s", "reason", "model", "hash", "PASS", "v1.0");
        Map<String, String> meta = p.toMetadata();
        assertThat(meta).containsEntry("scanVerdict", "PASS");
        assertThat(meta).containsEntry("scanVersion", "v1.0");
    }
}
