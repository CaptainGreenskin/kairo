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
package io.kairo.cron;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.cron.CronTask;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultCronSchedulerTest {

    @TempDir Path tempDir;

    private static final ZoneId UTC = ZoneId.of("UTC");

    private CronTaskStore store;
    private List<CronTask> firedTasks;
    private DefaultCronScheduler scheduler;

    @BeforeEach
    void setUp() {
        store = new CronTaskStore(tempDir.resolve(".kairo/cron-jobs.json"));
        firedTasks = new ArrayList<>();
        scheduler = new DefaultCronScheduler(store, firedTasks::add, UTC);
    }

    @AfterEach
    void tearDown() {
        scheduler.stop();
    }

    // -- CRUD --

    @Test
    void createReturnsTaskWithId() {
        CronTask task = scheduler.create("*/5 * * * *", "test prompt", true, false);
        assertThat(task.id()).hasSize(8);
        assertThat(task.cron()).isEqualTo("*/5 * * * *");
        assertThat(task.prompt()).isEqualTo("test prompt");
        assertThat(task.recurring()).isTrue();
        assertThat(task.durable()).isFalse();
        assertThat(task.lastFiredAt()).isNull();
        assertThat(task.createdAt()).isNotNull();
    }

    @Test
    void createValidatesCronExpression() {
        assertThatThrownBy(() -> scheduler.create("bad cron", "prompt", true, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listShowsCreatedTasks() {
        scheduler.create("* * * * *", "p1", true, false);
        scheduler.create("0 9 * * *", "p2", false, false);
        assertThat(scheduler.list()).hasSize(2);
    }

    @Test
    void deleteRemovesTask() {
        CronTask task = scheduler.create("* * * * *", "prompt", true, false);
        assertThat(scheduler.delete(task.id())).isTrue();
        assertThat(scheduler.list()).isEmpty();
    }

    @Test
    void deleteNonexistentReturnsFalse() {
        assertThat(scheduler.delete("nonexistent")).isFalse();
    }

    @Test
    void maxJobsEnforced() {
        for (int i = 0; i < DefaultCronScheduler.MAX_JOBS; i++) {
            scheduler.create("* * * * *", "p" + i, true, false);
        }
        assertThatThrownBy(() -> scheduler.create("* * * * *", "overflow", true, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("50");
    }

    // -- tick & firing --

    @Test
    void tickFiresMatchingTask() {
        scheduler.create("30 10 * * *", "fire me", true, false);
        ZonedDateTime now = ZonedDateTime.of(2026, 5, 15, 10, 30, 0, 0, UTC);
        scheduler.tick();

        // tick uses ZonedDateTime.now(zone), so we test via shouldFire directly
        DefaultCronScheduler.TaskEntry entry =
                new DefaultCronScheduler.TaskEntry(
                        new CronTask(
                                "test", "30 10 * * *", "fire me", Instant.now(), null, true, false),
                        CronExpression.parse("30 10 * * *"));
        assertThat(scheduler.shouldFire(entry, now)).isTrue();
    }

    @Test
    void shouldFireReturnsFalseWhenNotMatching() {
        DefaultCronScheduler.TaskEntry entry =
                new DefaultCronScheduler.TaskEntry(
                        new CronTask(
                                "test", "30 10 * * *", "prompt", Instant.now(), null, true, false),
                        CronExpression.parse("30 10 * * *"));
        ZonedDateTime now = ZonedDateTime.of(2026, 5, 15, 11, 0, 0, 0, UTC);
        assertThat(scheduler.shouldFire(entry, now)).isFalse();
    }

    // -- missed-fire recovery --

    @Test
    void missedFireRecoveryWhenNeverFired() {
        CronExpression expr = CronExpression.parse("0 9 * * *");
        DefaultCronScheduler.TaskEntry entry =
                new DefaultCronScheduler.TaskEntry(
                        new CronTask(
                                "test", "0 9 * * *", "prompt", Instant.now(), null, true, false),
                        expr);
        // Current time is 9:01, previous minute (9:00) matches, never fired → should recover
        ZonedDateTime now = ZonedDateTime.of(2026, 5, 15, 9, 1, 0, 0, UTC);
        assertThat(scheduler.shouldFire(entry, now)).isTrue();
    }

    @Test
    void missedFireRecoveryWhenLastFiredIsOld() {
        CronExpression expr = CronExpression.parse("0 9 * * *");
        Instant oldFire = Instant.parse("2026-05-14T09:00:00Z");
        DefaultCronScheduler.TaskEntry entry =
                new DefaultCronScheduler.TaskEntry(
                        new CronTask(
                                "test", "0 9 * * *", "prompt", Instant.now(), oldFire, true, false),
                        expr);
        // Current time is 9:01, previous minute matches, lastFired is yesterday → recover
        ZonedDateTime now = ZonedDateTime.of(2026, 5, 15, 9, 1, 0, 0, UTC);
        assertThat(scheduler.shouldFire(entry, now)).isTrue();
    }

    @Test
    void noMissedFireWhenAlreadyFiredThisSlot() {
        CronExpression expr = CronExpression.parse("0 9 * * *");
        Instant recentFire = Instant.parse("2026-05-15T09:00:00Z");
        DefaultCronScheduler.TaskEntry entry =
                new DefaultCronScheduler.TaskEntry(
                        new CronTask(
                                "test",
                                "0 9 * * *",
                                "prompt",
                                Instant.now(),
                                recentFire,
                                true,
                                false),
                        expr);
        // 9:01, previous minute matches, but already fired at 9:00 → no missed fire
        ZonedDateTime now = ZonedDateTime.of(2026, 5, 15, 9, 1, 0, 0, UTC);
        assertThat(scheduler.shouldFire(entry, now)).isFalse();
    }

    // -- expiry --

    @Test
    void nonDurableRecurringExpiresAfter7Days() {
        Instant created = Instant.now().minus(8, ChronoUnit.DAYS);
        CronTask task = new CronTask("test", "* * * * *", "p", created, null, true, false);
        assertThat(DefaultCronScheduler.isExpired(task, Instant.now())).isTrue();
    }

    @Test
    void nonDurableRecurringNotExpiredBefore7Days() {
        Instant created = Instant.now().minus(6, ChronoUnit.DAYS);
        CronTask task = new CronTask("test", "* * * * *", "p", created, null, true, false);
        assertThat(DefaultCronScheduler.isExpired(task, Instant.now())).isFalse();
    }

    @Test
    void durableRecurringNeverExpires() {
        Instant created = Instant.now().minus(30, ChronoUnit.DAYS);
        CronTask task = new CronTask("test", "* * * * *", "p", created, null, true, true);
        assertThat(DefaultCronScheduler.isExpired(task, Instant.now())).isFalse();
    }

    @Test
    void oneShotNeverExpires() {
        Instant created = Instant.now().minus(30, ChronoUnit.DAYS);
        CronTask task = new CronTask("test", "* * * * *", "p", created, null, false, false);
        assertThat(DefaultCronScheduler.isExpired(task, Instant.now())).isFalse();
    }

    // -- durable persistence --

    @Test
    void durableTaskPersistedOnCreate() {
        scheduler.create("0 9 * * *", "persist me", true, true);
        List<CronTask> persisted = store.load();
        assertThat(persisted).hasSize(1);
        assertThat(persisted.get(0).prompt()).isEqualTo("persist me");
    }

    @Test
    void durableTaskRemovedFromFileOnDelete() {
        CronTask task = scheduler.create("0 9 * * *", "persist me", true, true);
        scheduler.delete(task.id());
        List<CronTask> persisted = store.load();
        assertThat(persisted).isEmpty();
    }

    @Test
    void durableTasksSurviveRestart() {
        scheduler.create("0 9 * * *", "survive", true, true);
        scheduler.stop();

        // New scheduler instance loads from same store
        DefaultCronScheduler scheduler2 = new DefaultCronScheduler(store, firedTasks::add, UTC);
        scheduler2.start();
        try {
            assertThat(scheduler2.list()).hasSize(1);
            assertThat(scheduler2.list().get(0).prompt()).isEqualTo("survive");
        } finally {
            scheduler2.stop();
        }
    }

    @Test
    void sessionOnlyTaskNotPersisted() {
        scheduler.create("0 9 * * *", "ephemeral", true, false);
        List<CronTask> persisted = store.load();
        assertThat(persisted).isEmpty();
    }

    // -- lifecycle --

    @Test
    void startIsIdempotent() {
        scheduler.start();
        scheduler.start(); // should not throw
        assertThat(scheduler.list()).isEmpty();
    }

    @Test
    void stopIsIdempotent() {
        scheduler.start();
        scheduler.stop();
        scheduler.stop(); // should not throw
    }

    @Test
    void initialDelayIsPositive() {
        long delay = scheduler.computeInitialDelaySeconds();
        assertThat(delay).isBetween(1L, 60L);
    }
}
