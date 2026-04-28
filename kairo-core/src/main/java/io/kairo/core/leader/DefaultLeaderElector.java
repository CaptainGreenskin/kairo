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
package io.kairo.core.leader;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link LeaderElector} implementation that automatically refreshes the lease at 1/3 of the
 * lease duration to prevent expiry due to transient latency.
 *
 * <p>Lifecycle: call {@link #start()} to enable automatic renewal; call {@link #stop()} (or use
 * try-with-resources via {@link #close()}) to release the lease and shut down the scheduler.
 */
public final class DefaultLeaderElector implements LeaderElector, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DefaultLeaderElector.class);

    private final String nodeId;
    private final LeaderStore store;
    private final Duration leaseDuration;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> renewalTask;

    public DefaultLeaderElector(LeaderStore store, Duration leaseDuration) {
        this(UUID.randomUUID().toString(), store, leaseDuration);
    }

    public DefaultLeaderElector(String nodeId, LeaderStore store, Duration leaseDuration) {
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId");
        this.store = Objects.requireNonNull(store, "store");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration");
        this.scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t =
                                    new Thread(r, "kairo-leader-renew-" + nodeId.substring(0, 8));
                            t.setDaemon(true);
                            return t;
                        });
    }

    /**
     * Start automatic lease renewal. Attempts an initial {@link #tryAcquire}, then schedules
     * periodic renewals at {@code leaseDuration / 3} intervals.
     */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        tryAcquire(); // initial attempt
        long renewIntervalMs = Math.max(100L, leaseDuration.toMillis() / 3);
        renewalTask =
                scheduler.scheduleWithFixedDelay(
                        () -> {
                            if (!tryAcquire()) {
                                log.debug("Node '{}' failed to renew lease; not leader", nodeId);
                            }
                        },
                        renewIntervalMs,
                        renewIntervalMs,
                        TimeUnit.MILLISECONDS);
        log.info(
                "LeaderElector started for node '{}' (lease={}ms, renewInterval={}ms)",
                nodeId,
                leaseDuration.toMillis(),
                renewIntervalMs);
    }

    /** Stop renewal and release the lease. Idempotent. */
    public void stop() {
        if (!started.compareAndSet(true, false)) {
            return;
        }
        ScheduledFuture<?> task = renewalTask;
        if (task != null) {
            task.cancel(false);
        }
        scheduler.shutdown();
        store.release(nodeId);
        log.info("LeaderElector stopped for node '{}'", nodeId);
    }

    @Override
    public void close() {
        stop();
    }

    @Override
    public boolean tryAcquire() {
        return store.tryAcquire(nodeId, leaseDuration);
    }

    @Override
    public void release() {
        store.release(nodeId);
    }

    @Override
    public boolean isLeader() {
        return store.currentLease().map(lease -> lease.nodeId().equals(nodeId)).orElse(false);
    }

    public String nodeId() {
        return nodeId;
    }
}
