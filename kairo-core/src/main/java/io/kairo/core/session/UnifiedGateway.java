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
import io.kairo.api.message.Msg;
import io.kairo.core.agent.DefaultReActAgent;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class UnifiedGateway {

    private static final Logger log = LoggerFactory.getLogger(UnifiedGateway.class);
    private static final int DEFAULT_MAX_CONCURRENT = 16;

    private final AgentSessionPool pool;
    private final ConcurrentHashMap<SessionKey, Semaphore> callLocks = new ConcurrentHashMap<>();
    private final AtomicInteger activeRequests = new AtomicInteger(0);
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final int maxConcurrent;

    public UnifiedGateway(AgentSessionPool pool) {
        this(pool, DEFAULT_MAX_CONCURRENT);
    }

    public UnifiedGateway(AgentSessionPool pool, int maxConcurrent) {
        this.pool = pool;
        this.maxConcurrent = maxConcurrent;
    }

    public Mono<Msg> route(SessionKey key, Msg input) {
        if (draining.get()) {
            return Mono.error(new IllegalStateException("Gateway is shutting down"));
        }

        if (activeRequests.get() >= maxConcurrent) {
            return Mono.error(
                    new ConcurrencyLimitExceededException(
                            "Max concurrent requests reached (" + maxConcurrent + ")"));
        }

        Agent agent = pool.getOrCreate(key);
        Semaphore sem = callLocks.computeIfAbsent(key, k -> new Semaphore(1));

        return Mono.defer(
                () -> {
                    if (!sem.tryAcquire()) {
                        log.debug("Session [{}] busy, interrupting previous call", key);
                        agent.interrupt();
                        try {
                            sem.acquire();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return Mono.error(e);
                        }
                    }
                    activeRequests.incrementAndGet();
                    return agent.call(input)
                            .doFinally(
                                    signal -> {
                                        activeRequests.decrementAndGet();
                                        sem.release();
                                    });
                });
    }

    public void interrupt(SessionKey key) {
        Agent agent = pool.get(key);
        if (agent != null) {
            agent.interrupt();
            log.info("Interrupted session [{}]", key);
        }
    }

    public void switchModel(SessionKey key, Agent newAgent) {
        Agent oldAgent = pool.get(key);
        if (oldAgent instanceof DefaultReActAgent old) {
            List<Msg> history = old.conversationHistory();
            if (newAgent instanceof DefaultReActAgent newReAct && !history.isEmpty()) {
                newReAct.injectMessages(history);
                log.info(
                        "Transferred {} messages to new model agent for [{}]", history.size(), key);
            }
        }
        pool.replace(key, newAgent);
        log.info("Model switched for session [{}]", key);
    }

    public int activeRequestCount() {
        return activeRequests.get();
    }

    public boolean isDraining() {
        return draining.get();
    }

    public void startDrain() {
        draining.set(true);
        log.info("Gateway drain started, rejecting new requests");
    }

    public boolean awaitDrain(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (activeRequests.get() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return activeRequests.get() == 0;
    }

    public AgentSessionPool pool() {
        return pool;
    }

    public void shutdown() {
        draining.set(true);
        pool.shutdown();
        callLocks.clear();
    }
}
