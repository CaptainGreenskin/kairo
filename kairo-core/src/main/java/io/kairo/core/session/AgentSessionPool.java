/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.core.session;

import io.kairo.api.agent.Agent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AgentSessionPool {

    private static final Logger log = LoggerFactory.getLogger(AgentSessionPool.class);
    private static final long SWEEP_INTERVAL_SECONDS = 300;

    private final int maxSize;
    private final Duration idleTtl;
    private final Function<SessionKey, Agent> agentFactory;
    private final Consumer<SessionKey> onEviction;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final LinkedHashMap<SessionKey, PoolEntry> pool;
    private final ScheduledExecutorService evictor;

    public record PoolEntry(Agent agent, Instant lastAccess) {
        public PoolEntry withAccess() {
            return new PoolEntry(agent, Instant.now());
        }
    }

    public AgentSessionPool(
            int maxSize,
            Duration idleTtl,
            Function<SessionKey, Agent> agentFactory,
            Consumer<SessionKey> onEviction) {
        this.maxSize = maxSize;
        this.idleTtl = idleTtl;
        this.agentFactory = agentFactory;
        this.onEviction = onEviction != null ? onEviction : k -> {};
        this.pool = new LinkedHashMap<>(16, 0.75f, true);
        this.evictor =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "session-pool-evictor");
                            t.setDaemon(true);
                            return t;
                        });
        this.evictor.scheduleAtFixedRate(
                this::sweepIdle, SWEEP_INTERVAL_SECONDS, SWEEP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public AgentSessionPool(GatewayConfig config, Function<SessionKey, Agent> agentFactory) {
        this(config.poolMaxSize(), config.idleTtl(), agentFactory, null);
    }

    public Agent getOrCreate(SessionKey key) {
        lock.writeLock().lock();
        try {
            PoolEntry existing = pool.get(key);
            if (existing != null) {
                pool.put(key, existing.withAccess());
                return existing.agent();
            }

            Agent agent = agentFactory.apply(key);
            pool.put(key, new PoolEntry(agent, Instant.now()));
            log.info("Created agent session [{}], pool size: {}", key, pool.size());

            evictExcess();
            return agent;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Agent get(SessionKey key) {
        lock.readLock().lock();
        try {
            PoolEntry entry = pool.get(key);
            return entry != null ? entry.agent() : null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void evict(SessionKey key) {
        lock.writeLock().lock();
        try {
            PoolEntry removed = pool.remove(key);
            if (removed != null) {
                removed.agent().interrupt();
                onEviction.accept(key);
                log.info("Evicted agent session [{}], pool size: {}", key, pool.size());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Agent replace(SessionKey key, Agent newAgent) {
        lock.writeLock().lock();
        try {
            PoolEntry old = pool.remove(key);
            pool.put(key, new PoolEntry(newAgent, Instant.now()));
            if (old != null) {
                old.agent().interrupt();
            }
            log.info("Replaced agent session [{}]", key);
            return newAgent;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return pool.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void shutdown() {
        evictor.shutdownNow();
        lock.writeLock().lock();
        try {
            for (var entry : pool.values()) {
                entry.agent().interrupt();
            }
            pool.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void evictExcess() {
        while (pool.size() > maxSize) {
            var it = pool.entrySet().iterator();
            if (it.hasNext()) {
                Map.Entry<SessionKey, PoolEntry> eldest = it.next();
                it.remove();
                eldest.getValue().agent().interrupt();
                onEviction.accept(eldest.getKey());
                log.info("LRU evicted session [{}], pool size: {}", eldest.getKey(), pool.size());
            }
        }
    }

    private void sweepIdle() {
        Instant cutoff = Instant.now().minus(idleTtl);
        List<SessionKey> toEvict = new ArrayList<>();

        lock.readLock().lock();
        try {
            for (var entry : pool.entrySet()) {
                if (entry.getValue().lastAccess().isBefore(cutoff)) {
                    toEvict.add(entry.getKey());
                }
            }
        } finally {
            lock.readLock().unlock();
        }

        for (SessionKey key : toEvict) {
            evict(key);
        }

        if (!toEvict.isEmpty()) {
            log.info("Idle sweep evicted {} session(s)", toEvict.size());
        }
    }
}
