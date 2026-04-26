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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.plan.PlanFile;
import io.kairo.api.plan.PlanStatus;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link PlanFileManager}. */
class PlanFileManagerTest {

    @TempDir Path workingDir;

    private PlanFileManager manager;

    @BeforeEach
    void setUp() {
        manager = new PlanFileManager(workingDir);
    }

    // ===== createPlan =====

    @Test
    void createPlan_returnsNewDraftPlan() {
        PlanFile plan = manager.createPlan("My Plan");
        assertThat(plan.name()).isEqualTo("My Plan");
        assertThat(plan.status()).isEqualTo(PlanStatus.DRAFT);
        assertThat(plan.id()).isNotNull().isNotEmpty();
    }

    @Test
    void createPlan_persistedToDisk() {
        PlanFile created = manager.createPlan("Persisted Plan");
        PlanFile retrieved = manager.getPlan(created.id());
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.name()).isEqualTo("Persisted Plan");
        assertThat(retrieved.id()).isEqualTo(created.id());
    }

    // ===== getPlan =====

    @Test
    void getPlan_nonExistentId_returnsNull() {
        assertThat(manager.getPlan("does-not-exist")).isNull();
    }

    @Test
    void getPlan_existingId_returnsPlan() {
        PlanFile created = manager.createPlan("Test Plan");
        PlanFile retrieved = manager.getPlan(created.id());
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.status()).isEqualTo(PlanStatus.DRAFT);
    }

    // ===== listPlans =====

    @Test
    void listPlans_empty_returnsEmptyList() {
        assertThat(manager.listPlans()).isEmpty();
    }

    @Test
    void listPlans_multipleCreated_returnsAllNewestFirst() throws InterruptedException {
        PlanFile first = manager.createPlan("First");
        Thread.sleep(10);
        PlanFile second = manager.createPlan("Second");

        List<PlanFile> plans = manager.listPlans();
        assertThat(plans).hasSize(2);
        // Newest first: second before first
        assertThat(plans.get(0).id()).isEqualTo(second.id());
        assertThat(plans.get(1).id()).isEqualTo(first.id());
    }

    // ===== updatePlan =====

    @Test
    void updatePlan_updatesContentAndStatus() {
        PlanFile created = manager.createPlan("Update Me");
        PlanFile updated = manager.updatePlan(created.id(), "new content", PlanStatus.APPROVED);
        assertThat(updated.content()).isEqualTo("new content");
        assertThat(updated.status()).isEqualTo(PlanStatus.APPROVED);
    }

    @Test
    void updatePlan_persistsUpdate() {
        PlanFile created = manager.createPlan("Persist Update");
        manager.updatePlan(created.id(), "persisted content", PlanStatus.EXECUTING);
        PlanFile retrieved = manager.getPlan(created.id());
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.content()).isEqualTo("persisted content");
        assertThat(retrieved.status()).isEqualTo(PlanStatus.EXECUTING);
    }

    @Test
    void updatePlan_nonExistentId_throwsIllegalArgument() {
        assertThatThrownBy(() -> manager.updatePlan("no-such-id", "content", PlanStatus.DRAFT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ===== deletePlan =====

    @Test
    void deletePlan_existingId_returnsTrue() {
        PlanFile created = manager.createPlan("To Delete");
        assertThat(manager.deletePlan(created.id())).isTrue();
        assertThat(manager.getPlan(created.id())).isNull();
    }

    @Test
    void deletePlan_nonExistentId_returnsFalse() {
        assertThat(manager.deletePlan("ghost-id")).isFalse();
    }

    @Test
    void deletePlan_removedFromList() {
        PlanFile a = manager.createPlan("Plan A");
        manager.createPlan("Plan B");
        manager.deletePlan(a.id());
        List<PlanFile> plans = manager.listPlans();
        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).name()).isEqualTo("Plan B");
    }
}
