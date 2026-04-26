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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlanFileManagerTest {

    @TempDir Path tempDir;

    @Test
    void createPlanReturnsDraftStatus() {
        PlanFileManager manager = new PlanFileManager(tempDir);
        PlanFile plan = manager.createPlan("My Plan");
        assertThat(plan.status()).isEqualTo(PlanStatus.DRAFT);
    }

    @Test
    void createPlanStoresName() {
        PlanFileManager manager = new PlanFileManager(tempDir);
        PlanFile plan = manager.createPlan("Design Plan");
        assertThat(plan.name()).isEqualTo("Design Plan");
    }

    @Test
    void createPlanAssignsId() {
        PlanFileManager manager = new PlanFileManager(tempDir);
        PlanFile plan = manager.createPlan("Test");
        assertThat(plan.id()).isNotBlank();
    }

    @Test
    void getPlanReturnsNullForMissingId() {
        PlanFileManager manager = new PlanFileManager(tempDir);
        assertThat(manager.getPlan("nonexistent")).isNull();
    }

    @Test
    void getPlanRoundTrips() {
        PlanFileManager manager = new PlanFileManager(tempDir);
        PlanFile created = manager.createPlan("Round Trip");
        PlanFile loaded = manager.getPlan(created.id());
        assertThat(loaded).isNotNull();
        assertThat(loaded.name()).isEqualTo("Round Trip");
        assertThat(loaded.status()).isEqualTo(PlanStatus.DRAFT);
    }

    @Test
    void updatePlanChangesContentAndStatus() {
        PlanFileManager manager = new PlanFileManager(tempDir);
        PlanFile plan = manager.createPlan("Update Me");
        PlanFile updated = manager.updatePlan(plan.id(), "## Step 1", PlanStatus.APPROVED);
        assertThat(updated.content()).isEqualTo("## Step 1");
        assertThat(updated.status()).isEqualTo(PlanStatus.APPROVED);
    }

    @Test
    void updatePlanThrowsForMissingId() {
        PlanFileManager manager = new PlanFileManager(tempDir);
        assertThatThrownBy(() -> manager.updatePlan("missing", "content", PlanStatus.APPROVED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listPlansReturnsAllCreated() {
        PlanFileManager manager = new PlanFileManager(tempDir);
        manager.createPlan("Alpha");
        manager.createPlan("Beta");
        List<PlanFile> plans = manager.listPlans();
        assertThat(plans).hasSize(2);
    }

    @Test
    void listPlansEmptyWhenNoneCreated() {
        PlanFileManager manager = new PlanFileManager(tempDir);
        assertThat(manager.listPlans()).isEmpty();
    }

    @Test
    void deletePlanReturnsTrueOnSuccess() {
        PlanFileManager manager = new PlanFileManager(tempDir);
        PlanFile plan = manager.createPlan("To Delete");
        assertThat(manager.deletePlan(plan.id())).isTrue();
        assertThat(manager.getPlan(plan.id())).isNull();
    }

    @Test
    void deletePlanReturnsFalseForMissingId() {
        PlanFileManager manager = new PlanFileManager(tempDir);
        assertThat(manager.deletePlan("does-not-exist")).isFalse();
    }
}
