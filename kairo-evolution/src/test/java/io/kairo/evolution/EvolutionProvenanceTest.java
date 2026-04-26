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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EvolutionProvenanceTest {

    private static EvolutionProvenance provenance(String scanVerdict, String scanVersion) {
        return new EvolutionProvenance(
                "session-abc",
                "performance_drop",
                "claude-opus-4-7",
                "sha256:deadbeef",
                scanVerdict,
                scanVersion);
    }

    @Test
    void recordFieldsAreAccessible() {
        EvolutionProvenance p = provenance("PASS", "v2");
        assertEquals("session-abc", p.sourceSessionId());
        assertEquals("performance_drop", p.triggerReason());
        assertEquals("claude-opus-4-7", p.reviewModel());
        assertEquals("sha256:deadbeef", p.contentHash());
        assertEquals("PASS", p.scanVerdict());
        assertEquals("v2", p.scanVersion());
    }

    @Test
    void toMetadataContainsRequiredKeys() {
        Map<String, String> meta = provenance("PASS", "v2").toMetadata();
        assertEquals("session-abc", meta.get("sourceSessionId"));
        assertEquals("performance_drop", meta.get("triggerReason"));
        assertEquals("claude-opus-4-7", meta.get("reviewModel"));
        assertEquals("sha256:deadbeef", meta.get("contentHash"));
        assertEquals("PASS", meta.get("scanVerdict"));
        assertEquals("v2", meta.get("scanVersion"));
    }

    @Test
    void toMetadataOmitsNullScanVerdict() {
        Map<String, String> meta = provenance(null, null).toMetadata();
        assertFalse(meta.containsKey("scanVerdict"));
        assertFalse(meta.containsKey("scanVersion"));
        assertEquals(4, meta.size());
    }

    @Test
    void toMetadataOmitsOnlyNullScanVersion() {
        Map<String, String> meta = provenance("REJECT", null).toMetadata();
        assertTrue(meta.containsKey("scanVerdict"));
        assertFalse(meta.containsKey("scanVersion"));
        assertEquals(5, meta.size());
    }

    @Test
    void toMetadataIsUnmodifiable() {
        Map<String, String> meta = provenance("PASS", "v1").toMetadata();
        assertThrows(UnsupportedOperationException.class, () -> meta.put("extra", "value"));
    }

    @Test
    void recordEquality() {
        EvolutionProvenance a = provenance("PASS", "v1");
        EvolutionProvenance b = provenance("PASS", "v1");
        EvolutionProvenance c = provenance("REJECT", "v1");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void toStringContainsSessionId() {
        String str = provenance("PASS", "v1").toString();
        assertTrue(str.contains("session-abc"));
    }
}
