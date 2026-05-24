/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.cron;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileLockingCronSchedulerTest {

    @Test
    void onlyLeaseHolderFiresTickInTwoSchedulersSharingSameLockFile(@TempDir Path tmp) {
        Path lockFile = tmp.resolve("cron.lock");
        Path storeA = tmp.resolve("a-tasks.json");
        Path storeB = tmp.resolve("b-tasks.json");
        AtomicInteger fires = new AtomicInteger();

        var schedA =
                FileLockingCronScheduler.wrap(
                        lockFile,
                        new CronTaskStore(storeA),
                        task -> fires.incrementAndGet(),
                        ZoneId.systemDefault(),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(5));
        var schedB =
                FileLockingCronScheduler.wrap(
                        lockFile,
                        new CronTaskStore(storeB),
                        task -> fires.incrementAndGet(),
                        ZoneId.systemDefault(),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(5));

        // Both register the same "every minute" task into their own in-memory copy.
        schedA.create("* * * * *", "a", true, false);
        schedB.create("* * * * *", "b", true, false);

        // The two schedulers share the lock file — A acquires first; B can't until A releases.
        boolean a = schedA.lease().tryAcquireOrRenew();
        boolean b = schedB.lease().tryAcquireOrRenew();
        assertThat(a).as("A acquires first").isTrue();
        assertThat(b).as("B can't acquire while A holds").isFalse();
        // Sanity: fires counter unused for this assertion (no ticks happened).
        assertThat(fires.get()).isZero();

        // A releases → B can acquire on next call.
        schedA.lease().release();
        assertThat(schedB.lease().tryAcquireOrRenew()).as("B takes over after release").isTrue();
        schedB.lease().release();
    }

    @Test
    void expiredLeaseGetsTakenOver(@TempDir Path tmp) throws Exception {
        Path lockFile = tmp.resolve("cron.lock");
        FileLeaseLock a =
                new FileLeaseLock(lockFile, "a", Duration.ofMillis(100), Duration.ofMillis(50));
        FileLeaseLock b =
                new FileLeaseLock(lockFile, "b", Duration.ofMillis(100), Duration.ofMillis(50));

        assertThat(a.tryAcquireOrRenew()).isTrue();
        // Sleep past the TTL so B can take over.
        Thread.sleep(150);
        assertThat(b.tryAcquireOrRenew()).as("B takes over expired lease").isTrue();
    }

    @Test
    void renewKeepsLeaseAlive(@TempDir Path tmp) throws Exception {
        Path lockFile = tmp.resolve("cron.lock");
        FileLeaseLock a =
                new FileLeaseLock(lockFile, "a", Duration.ofMillis(200), Duration.ofMillis(10));
        FileLeaseLock b =
                new FileLeaseLock(lockFile, "b", Duration.ofMillis(200), Duration.ofMillis(10));

        assertThat(a.tryAcquireOrRenew()).isTrue();
        // Renew several times within TTL — B should never get to take over.
        for (int i = 0; i < 5; i++) {
            Thread.sleep(30);
            a.tryAcquireOrRenew();
            assertThat(b.tryAcquireOrRenew()).as("B blocked on iter " + i).isFalse();
        }
    }
}
