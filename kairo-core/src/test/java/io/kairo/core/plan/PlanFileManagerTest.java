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
package io.kairo.core.plan;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.plan.PlanFile;
import io.kairo.api.plan.PlanStatus;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlanFileManagerTest {

    @TempDir Path tempDir;

    private PlanFileManager manager;

    @BeforeEach
    void setUp() {
        manager = new PlanFileManager(tempDir);
    }

    @Test
    void createPlanReturnsNonNull() {
        PlanFile plan = manager.createPlan("My Plan");
        assertNotNull(plan);
    }

    @Test
    void createPlanHasGivenName() {
        PlanFile plan = manager.createPlan("Alpha Plan");
        assertEquals("Alpha Plan", plan.name());
    }

    @Test
    void createPlanStatusIsDraft() {
        PlanFile plan = manager.createPlan("Draft Test");
        assertEquals(PlanStatus.DRAFT, plan.status());
    }

    @Test
    void createPlanHasNonNullId() {
        PlanFile plan = manager.createPlan("Id Test");
        assertNotNull(plan.id());
        assertFalse(plan.id().isBlank());
    }

    @Test
    void createPlanIdsAreUnique() {
        PlanFile a = manager.createPlan("Plan A");
        PlanFile b = manager.createPlan("Plan B");
        assertNotEquals(a.id(), b.id());
    }

    @Test
    void createPlanHasNonNullCreatedAt() {
        PlanFile plan = manager.createPlan("Time Test");
        assertNotNull(plan.createdAt());
    }

    @Test
    void getPlanReturnsCreatedPlan() {
        PlanFile created = manager.createPlan("Retrieve Me");
        PlanFile retrieved = manager.getPlan(created.id());
        assertNotNull(retrieved);
        assertEquals(created.id(), retrieved.id());
        assertEquals(created.name(), retrieved.name());
    }

    @Test
    void getPlanReturnsNullForMissingId() {
        assertNull(manager.getPlan("nonexistent"));
    }

    @Test
    void listPlansEmptyInitially() {
        List<PlanFile> plans = manager.listPlans();
        assertTrue(plans.isEmpty());
    }

    @Test
    void listPlansReturnsCreatedPlan() {
        manager.createPlan("Listed Plan");
        List<PlanFile> plans = manager.listPlans();
        assertEquals(1, plans.size());
        assertEquals("Listed Plan", plans.get(0).name());
    }

    @Test
    void listPlansReturnsAllCreatedPlans() {
        manager.createPlan("Plan One");
        manager.createPlan("Plan Two");
        manager.createPlan("Plan Three");
        assertEquals(3, manager.listPlans().size());
    }

    @Test
    void updatePlanChangesStatus() {
        PlanFile created = manager.createPlan("Status Test");
        PlanFile updated = manager.updatePlan(created.id(), "content", PlanStatus.APPROVED);
        assertEquals(PlanStatus.APPROVED, updated.status());
    }

    @Test
    void updatePlanChangesContent() {
        PlanFile created = manager.createPlan("Content Test");
        PlanFile updated = manager.updatePlan(created.id(), "new content here", PlanStatus.DRAFT);
        assertEquals("new content here", updated.content());
    }

    @Test
    void updatePlanPreservesNameAndId() {
        PlanFile created = manager.createPlan("Preserved Name");
        PlanFile updated = manager.updatePlan(created.id(), "x", PlanStatus.EXECUTING);
        assertEquals(created.id(), updated.id());
        assertEquals(created.name(), updated.name());
    }

    @Test
    void updatePlanThrowsForMissingId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> manager.updatePlan("missing-id", "content", PlanStatus.APPROVED));
    }

    @Test
    void updatedPlanPersistedToGetPlan() {
        PlanFile created = manager.createPlan("Persist Test");
        manager.updatePlan(created.id(), "persisted content", PlanStatus.COMPLETED);
        PlanFile retrieved = manager.getPlan(created.id());
        assertNotNull(retrieved);
        assertEquals(PlanStatus.COMPLETED, retrieved.status());
        assertEquals("persisted content", retrieved.content());
    }

    @Test
    void deletePlanReturnsTrueForExistingPlan() {
        PlanFile plan = manager.createPlan("To Delete");
        assertTrue(manager.deletePlan(plan.id()));
    }

    @Test
    void deletePlanReturnsFalseForMissingPlan() {
        assertFalse(manager.deletePlan("does-not-exist"));
    }

    @Test
    void deletePlanRemovesPlanFromList() {
        PlanFile plan = manager.createPlan("Deleted Plan");
        manager.deletePlan(plan.id());
        assertTrue(manager.listPlans().isEmpty());
    }
}
