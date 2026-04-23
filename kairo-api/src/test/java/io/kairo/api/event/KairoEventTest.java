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
package io.kairo.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class KairoEventTest {

    @Test
    void ofProducesDomainTaggedEnvelope() {
        KairoEvent event =
                KairoEvent.of(
                        KairoEvent.DOMAIN_EXECUTION,
                        "MODEL_CALL_REQUEST",
                        Map.of("model", "gpt-4"));
        assertEquals(KairoEvent.DOMAIN_EXECUTION, event.domain());
        assertEquals("MODEL_CALL_REQUEST", event.eventType());
        assertEquals("gpt-4", event.attributes().get("model"));
        assertNotNull(event.eventId());
        assertNotNull(event.timestamp());
    }

    @Test
    void wrapPreservesPayload() {
        String payload = "payload";
        KairoEvent event = KairoEvent.wrap(KairoEvent.DOMAIN_EVOLUTION, "SKILL_CREATED", payload);
        assertEquals(payload, event.payload());
        assertTrue(event.attributes().isEmpty());
    }

    @Test
    void rejectsNullRequiredFields() {
        assertThrows(
                NullPointerException.class,
                () -> new KairoEvent(null, java.time.Instant.now(), "execution", "X", null, null));
    }
}
