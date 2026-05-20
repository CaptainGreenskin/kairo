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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of "last output" per cron task id. The cron callback writes the agent's final
 * response here, then any downstream task whose {@code contextFromTaskId} points at the upstream id
 * can prepend that output to its own prompt before firing.
 *
 * <p>Bounded by {@link #MAX_ENTRIES} (LRU eviction) so a runaway task chain doesn't OOM us.
 */
public final class CronChainContext {

    public static final int MAX_ENTRIES = 256;

    private final ConcurrentHashMap<String, String> lastOutputs = new ConcurrentHashMap<>();

    public void recordOutput(String taskId, String output) {
        if (taskId == null) return;
        if (lastOutputs.size() >= MAX_ENTRIES) {
            // Crude eviction: drop the first iteration entry — good enough for chain semantics.
            lastOutputs.keySet().stream().findFirst().ifPresent(lastOutputs::remove);
        }
        lastOutputs.put(taskId, output == null ? "" : output);
    }

    public Optional<String> lastOutput(String taskId) {
        return Optional.ofNullable(taskId == null ? null : lastOutputs.get(taskId));
    }

    public Map<String, String> snapshot() {
        return Map.copyOf(lastOutputs);
    }

    public void clear() {
        lastOutputs.clear();
    }
}
