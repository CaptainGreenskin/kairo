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

import io.kairo.api.cron.CronScheduler;
import io.kairo.api.cron.CronTask;
import io.kairo.api.cron.CronTaskOptions;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link CronScheduler} decorator that only fires durable tasks when this JVM holds a {@link
 * FileLeaseLock} — i.e. distributed / multi-replica deployments don't double-fire a job because
 * three Kairo pods all happened to install the same scheduler.
 *
 * <p>Two-tier semantics: the underlying scheduler still ticks (and handles non-durable session-only
 * tasks normally — those are by definition not visible to other JVMs). For <strong>durable</strong>
 * tasks the wrapper interposes a {@code BooleanSupplier} idle gate that returns true only when we
 * own the lease.
 *
 * <p>Recommended construction via {@link #wrap(Path, CronTaskStore, ...)} which builds a {@link
 * DefaultCronScheduler} with the right idle gate already plugged in.
 *
 * <h2>Why a file lease and not e.g. ZooKeeper?</h2>
 *
 * <p>Cron is process-coordinated, not distributed-system-grade. Heartbeat file on a shared
 * filesystem is enough for the "k8s replicas all see the same NFS mount" case without dragging in a
 * coordination service. Hosts that need stronger guarantees can implement {@code CronScheduler}
 * themselves around their preferred coordinator.
 */
public final class FileLockingCronScheduler implements CronScheduler, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(FileLockingCronScheduler.class);

    private final DefaultCronScheduler delegate;
    private final FileLeaseLock lease;

    public FileLockingCronScheduler(DefaultCronScheduler delegate, FileLeaseLock lease) {
        this.delegate = delegate;
        this.lease = lease;
    }

    /**
     * Build a delegate with the lease wired in as its idle gate so durable tasks only fire while we
     * own the lock.
     */
    public static FileLockingCronScheduler wrap(
            java.nio.file.Path lockFile,
            CronTaskStore store,
            io.kairo.api.cron.CronFireCallback callback,
            ZoneId zone,
            Duration leaseTtl,
            Duration renewInterval) {
        FileLeaseLock lock = new FileLeaseLock(lockFile, leaseTtl, renewInterval);
        DefaultCronScheduler delegate =
                new DefaultCronScheduler(store, callback, zone, lock::tryAcquireOrRenew);
        return new FileLockingCronScheduler(delegate, lock);
    }

    public boolean ownsLease() {
        return lease.ownsLease();
    }

    public FileLeaseLock lease() {
        return lease;
    }

    @Override
    public CronTask create(String cron, String prompt, boolean recurring, boolean durable) {
        return delegate.create(cron, prompt, recurring, durable);
    }

    @Override
    public CronTask create(String cron, String prompt, CronTaskOptions options) {
        return delegate.create(cron, prompt, options);
    }

    @Override
    public boolean delete(String taskId) {
        return delegate.delete(taskId);
    }

    @Override
    public List<CronTask> list() {
        return delegate.list();
    }

    @Override
    public Optional<CronTask> pause(String taskId) {
        return delegate.pause(taskId);
    }

    @Override
    public Optional<CronTask> resume(String taskId) {
        return delegate.resume(taskId);
    }

    @Override
    public Optional<CronTask> edit(String taskId, String newCron, String newPrompt) {
        return delegate.edit(taskId, newCron, newPrompt);
    }

    @Override
    public boolean trigger(String taskId) {
        return delegate.trigger(taskId);
    }

    @Override
    public void start() {
        delegate.start();
        log.info(
                "FileLockingCronScheduler started (lockFile={}, owner={})",
                lease.lockFile(),
                lease.ownerId());
    }

    @Override
    public void stop() {
        try {
            delegate.stop();
        } finally {
            lease.release();
        }
    }

    @Override
    public void close() {
        stop();
    }
}
