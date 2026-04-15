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
package io.kairo.api.plan;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PlanApiTest {

    @Test
    void planFileFields() {
        Instant now = Instant.now();
        PlanFile plan = new PlanFile("p-1", "deploy plan", "Step 1: build", now, PlanStatus.DRAFT);

        assertEquals("p-1", plan.id());
        assertEquals("deploy plan", plan.name());
        assertEquals("Step 1: build", plan.content());
        assertEquals(now, plan.createdAt());
        assertEquals(PlanStatus.DRAFT, plan.status());
    }

    @Test
    void planStatusValues() {
        PlanStatus[] values = PlanStatus.values();
        assertEquals(4, values.length);
        assertNotNull(PlanStatus.valueOf("DRAFT"));
        assertNotNull(PlanStatus.valueOf("APPROVED"));
        assertNotNull(PlanStatus.valueOf("EXECUTING"));
        assertNotNull(PlanStatus.valueOf("COMPLETED"));
    }

    @Test
    void planStatusInvalidValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> PlanStatus.valueOf("INVALID"));
    }
}
