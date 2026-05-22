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
package io.kairo.evolution.curator;

import io.kairo.api.evolution.SkillLifecycleState;
import io.kairo.api.evolution.SkillTelemetry;
import io.kairo.api.evolution.SkillTelemetryStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hermes-style curator daemon. Replaces the old destructive-delete curator with non-destructive
 * lifecycle transitions:
 *
 * <ul>
 *   <li>{@link SkillLifecycleState#ACTIVE} → {@link SkillLifecycleState#STALE} once the last
 *       activity is older than {@link CuratorConfig#staleAfter()}.
 *   <li>{@link SkillLifecycleState#STALE} → {@link SkillLifecycleState#ARCHIVED} once the last
 *       activity is older than {@link CuratorConfig#archiveAfter()}. Archive is reversible.
 *   <li>{@link SkillLifecycleState#STALE} → {@link SkillLifecycleState#ACTIVE} if the skill was
 *       used again after being marked stale.
 * </ul>
 *
 * <p>Pinned and non-agent-created skills are skipped (see {@link
 * SkillTelemetry#isCuratorImmune()}).
 *
 * <p>Each scheduled tick honors an {@link CuratorIdleSignal}: if the host has not been idle long
 * enough, the tick is a no-op (mirrors Hermes' {@code min_idle_hours} gate). The daemon also
 * respects {@link CuratorConfig#paused()} and {@link CuratorConfig#enabled()}; both can be flipped
 * at runtime via {@link #updateConfig(CuratorConfig)}.
 */
public final class LifecycleCuratorDaemon {

    private static final Logger log = LoggerFactory.getLogger(LifecycleCuratorDaemon.class);

    private final SkillTelemetryStore store;
    private final CuratorIdleSignal idleSignal;
    private final AtomicReference<CuratorConfig> config;
    private final AtomicReference<Instant> lastRunAt = new AtomicReference<>();
    private final AtomicReference<LifecycleTransitionResult> lastResult = new AtomicReference<>();
    private volatile ScheduledExecutorService scheduler;

    public LifecycleCuratorDaemon(SkillTelemetryStore store) {
        this(store, CuratorIdleSignal.alwaysIdle(), CuratorConfig.defaults());
    }

    public LifecycleCuratorDaemon(
            SkillTelemetryStore store, CuratorIdleSignal idleSignal, CuratorConfig config) {
        this.store = store;
        this.idleSignal = idleSignal;
        this.config = new AtomicReference<>(config);
    }

    /** Start the background scheduler. Idempotent — does nothing if already running. */
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "kairo-curator");
                            t.setDaemon(true);
                            return t;
                        });
        long minutes = Math.max(1, config.get().reviewInterval().toMinutes());
        scheduler.scheduleAtFixedRate(this::tickQuietly, minutes, minutes, TimeUnit.MINUTES);
        log.info(
                "LifecycleCuratorDaemon started, interval={}, stale={}, archive={}",
                config.get().reviewInterval(),
                config.get().staleAfter(),
                config.get().archiveAfter());
    }

    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
            log.info("LifecycleCuratorDaemon stopped");
        }
    }

    public boolean isRunning() {
        return scheduler != null && !scheduler.isShutdown();
    }

    public Instant lastRunAt() {
        return lastRunAt.get();
    }

    public LifecycleTransitionResult lastResult() {
        return lastResult.get();
    }

    public CuratorConfig config() {
        return config.get();
    }

    /**
     * Update the config atomically. Does not restart the scheduler — interval changes apply next
     * start.
     */
    public void updateConfig(CuratorConfig newConfig) {
        config.set(newConfig);
    }

    private void tickQuietly() {
        try {
            runOnce();
        } catch (Throwable t) {
            log.warn("Curator tick failed: {}", t.toString());
        }
    }

    /**
     * Run a single curator pass synchronously and return the transition summary. Callers that want
     * to skip the gates (e.g. a manual {@code POST /curator/run}) should call {@link
     * #runOnce(boolean)} with {@code force=true}.
     */
    public LifecycleTransitionResult runOnce() {
        return runOnce(false);
    }

    /**
     * @param force when true, skip the enabled / paused / idle gates and run immediately.
     */
    public LifecycleTransitionResult runOnce(boolean force) {
        CuratorConfig cfg = config.get();
        Instant now = Instant.now();

        if (!force) {
            if (!cfg.enabled()) {
                log.debug("Curator disabled — skipping");
                return empty(now);
            }
            if (cfg.paused()) {
                log.debug("Curator paused — skipping");
                return empty(now);
            }
            if (!idleSignal.idleFor(cfg.idleThreshold())) {
                log.debug("Curator skipped: host not idle for {}", cfg.idleThreshold());
                return empty(now);
            }
        }

        List<SkillTelemetry> all = store.list().collectList().block();
        if (all == null || all.isEmpty()) {
            LifecycleTransitionResult empty = empty(now);
            lastRunAt.set(now);
            lastResult.set(empty);
            return empty;
        }

        Instant staleCutoff = now.minus(cfg.staleAfter());
        Instant archiveCutoff = now.minus(cfg.archiveAfter());

        List<String> markedStale = new ArrayList<>();
        List<String> archived = new ArrayList<>();
        List<String> reactivated = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int checked = 0;

        // Sort for deterministic iteration order (tests rely on this).
        all.sort(Comparator.comparing(SkillTelemetry::skillName));

        for (SkillTelemetry t : all) {
            checked++;
            if (t.isCuratorImmune()) {
                skipped.add(t.skillName());
                continue;
            }
            Instant anchor = t.lastActivityAt();
            if (anchor == null) {
                // Never active — use createdAt so brand-new skills aren't archived on day one.
                anchor = t.createdAt();
            }

            SkillLifecycleState current = t.state();
            if (!anchor.isAfter(archiveCutoff) && current != SkillLifecycleState.ARCHIVED) {
                store.archive(t.skillName(), null, now).block();
                archived.add(t.skillName());
            } else if (!anchor.isAfter(staleCutoff) && current == SkillLifecycleState.ACTIVE) {
                store.setState(t.skillName(), SkillLifecycleState.STALE, now).block();
                markedStale.add(t.skillName());
            } else if (anchor.isAfter(staleCutoff) && current == SkillLifecycleState.STALE) {
                store.setState(t.skillName(), SkillLifecycleState.ACTIVE, now).block();
                reactivated.add(t.skillName());
            }
        }

        LifecycleTransitionResult result =
                new LifecycleTransitionResult(
                        now, checked, markedStale, archived, reactivated, skipped);
        lastRunAt.set(now);
        lastResult.set(result);
        log.info(
                "Curator pass: checked={} stale={} archived={} reactivated={} skipped={}",
                checked,
                markedStale.size(),
                archived.size(),
                reactivated.size(),
                skipped.size());
        return result;
    }

    private static LifecycleTransitionResult empty(Instant now) {
        return new LifecycleTransitionResult(now, 0, List.of(), List.of(), List.of(), List.of());
    }

    /** Returns the live interval, mostly for tests. */
    Duration intervalForTest() {
        return config.get().reviewInterval();
    }
}
