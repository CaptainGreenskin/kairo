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
package io.kairo.core.cron;

import io.kairo.api.cron.CronTask;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link CronScheduler} that ticks every 60 seconds aligned to the minute boundary,
 * evaluates all registered cron tasks, and fires matching ones via a {@link CronFireCallback}.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Dual storage: in-memory (all tasks) + file-based (durable tasks only)
 *   <li>Missed-fire recovery: if a task should have fired but didn't, fires on next tick
 *   <li>One-shot tasks auto-delete after firing
 *   <li>Non-durable recurring tasks expire after 7 days
 *   <li>Max 50 concurrent tasks
 * </ul>
 */
public class DefaultCronScheduler implements CronScheduler {

    private static final Logger log = LoggerFactory.getLogger(DefaultCronScheduler.class);

    static final int MAX_JOBS = 50;
    static final Duration SESSION_EXPIRY = Duration.ofDays(7);

    private final CronTaskStore store;
    private final CronFireCallback callback;
    private final ZoneId zone;

    private final Map<String, TaskEntry> tasks = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> tickFuture;

    public DefaultCronScheduler(CronTaskStore store, CronFireCallback callback, ZoneId zone) {
        this.store = store;
        this.callback = callback;
        this.zone = zone;
    }

    @Override
    public CronTask create(String cron, String prompt, boolean recurring, boolean durable) {
        if (tasks.size() >= MAX_JOBS) {
            throw new IllegalStateException(
                    "Maximum number of cron jobs (" + MAX_JOBS + ") reached");
        }
        CronExpression expr = CronExpression.parse(cron);
        String id = generateId();
        CronTask task = new CronTask(id, cron, prompt, Instant.now(), null, recurring, durable);
        tasks.put(id, new TaskEntry(task, expr));
        if (durable) {
            persistDurableTasks();
        }
        log.info(
                "Created cron task {} [cron={}, recurring={}, durable={}]",
                id,
                cron,
                recurring,
                durable);
        return task;
    }

    @Override
    public boolean delete(String taskId) {
        TaskEntry removed = tasks.remove(taskId);
        if (removed == null) {
            return false;
        }
        if (removed.task.durable()) {
            persistDurableTasks();
        }
        log.info("Deleted cron task {}", taskId);
        return true;
    }

    @Override
    public List<CronTask> list() {
        return tasks.values().stream().map(e -> e.task).toList();
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        loadDurableTasks();

        executor =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "kairo-cron-scheduler");
                            t.setDaemon(true);
                            return t;
                        });

        long initialDelay = computeInitialDelaySeconds();
        tickFuture = executor.scheduleAtFixedRate(this::tick, initialDelay, 60, TimeUnit.SECONDS);
        log.info(
                "Cron scheduler started (initial delay={}s, {} durable tasks loaded)",
                initialDelay,
                tasks.size());
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (tickFuture != null) {
            tickFuture.cancel(false);
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        persistDurableTasks();
        tasks.clear();
        log.info("Cron scheduler stopped");
    }

    // -- tick logic --

    void tick() {
        try {
            ZonedDateTime now = ZonedDateTime.now(zone).truncatedTo(ChronoUnit.MINUTES);
            List<String> toRemove = new ArrayList<>();

            for (Map.Entry<String, TaskEntry> entry : tasks.entrySet()) {
                String id = entry.getKey();
                TaskEntry te = entry.getValue();
                CronTask task = te.task;

                if (isExpired(task, now.toInstant())) {
                    toRemove.add(id);
                    continue;
                }

                if (shouldFire(te, now)) {
                    fireTask(te, now);
                    if (!task.recurring()) {
                        toRemove.add(id);
                    }
                }
            }

            boolean durableChanged = false;
            for (String id : toRemove) {
                TaskEntry removed = tasks.remove(id);
                if (removed != null && removed.task.durable()) {
                    durableChanged = true;
                }
                log.debug("Removed cron task {} (expired or one-shot)", id);
            }
            if (durableChanged) {
                persistDurableTasks();
            }
        } catch (Exception e) {
            log.error("Error in cron scheduler tick", e);
        }
    }

    boolean shouldFire(TaskEntry entry, ZonedDateTime now) {
        if (entry.expression.matches(now)) {
            return true;
        }
        // Missed-fire recovery: check if the previous minute matched and wasn't fired
        if (entry.task.lastFiredAt() != null) {
            ZonedDateTime lastFired =
                    entry.task.lastFiredAt().atZone(zone).truncatedTo(ChronoUnit.MINUTES);
            ZonedDateTime previousMinute = now.minusMinutes(1);
            if (lastFired.isBefore(previousMinute) && entry.expression.matches(previousMinute)) {
                return true;
            }
        } else {
            // Never fired — check previous minute
            ZonedDateTime previousMinute = now.minusMinutes(1);
            if (entry.expression.matches(previousMinute)) {
                return true;
            }
        }
        return false;
    }

    private void fireTask(TaskEntry entry, ZonedDateTime now) {
        try {
            callback.onFire(entry.task);
            CronTask updated = entry.task.withLastFiredAt(now.toInstant());
            entry.task = updated;
            tasks.put(updated.id(), entry);
            if (updated.durable()) {
                persistDurableTasks();
            }
            log.debug("Fired cron task {} [cron={}]", updated.id(), updated.cron());
        } catch (Exception e) {
            log.error("Error firing cron task {}: {}", entry.task.id(), e.getMessage(), e);
        }
    }

    static boolean isExpired(CronTask task, Instant now) {
        if (!task.recurring()) {
            return false;
        }
        if (task.durable()) {
            return false;
        }
        return task.createdAt().plus(SESSION_EXPIRY).isBefore(now);
    }

    // -- persistence --

    private void loadDurableTasks() {
        List<CronTask> durables = store.load();
        for (CronTask task : durables) {
            try {
                CronExpression expr = CronExpression.parse(task.cron());
                tasks.put(task.id(), new TaskEntry(task, expr));
            } catch (IllegalArgumentException e) {
                log.warn(
                        "Skipping persisted cron task {} with invalid expression '{}': {}",
                        task.id(),
                        task.cron(),
                        e.getMessage());
            }
        }
    }

    private void persistDurableTasks() {
        List<CronTask> durables =
                tasks.values().stream().map(e -> e.task).filter(CronTask::durable).toList();
        store.save(durables);
    }

    // -- helpers --

    long computeInitialDelaySeconds() {
        int currentSecond = ZonedDateTime.now(zone).getSecond();
        return currentSecond == 0 ? 60 : (60 - currentSecond);
    }

    private static String generateId() {
        return String.format("%08x", ThreadLocalRandom.current().nextInt());
    }

    // -- inner types --

    static class TaskEntry {

        CronTask task;
        final CronExpression expression;

        TaskEntry(CronTask task, CronExpression expression) {
            this.task = task;
            this.expression = expression;
        }
    }
}
