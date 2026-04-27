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
package io.kairo.skill;

import io.kairo.api.skill.SkillRegistry;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Watches a skill directory for *.md file changes and hot-reloads affected skills. */
public class SkillHotReloadWatcher implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SkillHotReloadWatcher.class);
    private static final long DEBOUNCE_MS = 500;

    private final Path skillDir;
    private final SkillLoader loader;
    private final SkillRegistry registry;
    private final List<java.util.function.Consumer<SkillReloadEvent>> listeners =
            new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService debouncer = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<Path, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean running;

    public SkillHotReloadWatcher(Path skillDir, SkillLoader loader, SkillRegistry registry) {
        this.skillDir = skillDir;
        this.loader = loader;
        this.registry = registry;
    }

    public void addListener(java.util.function.Consumer<SkillReloadEvent> listener) {
        listeners.add(listener);
    }

    public void start() throws IOException {
        if (running) {
            return;
        }
        watchService = FileSystems.getDefault().newWatchService();
        skillDir.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);

        running = true;
        watchThread = new Thread(this::watchLoop, "kairo-skill-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
        log.info("SkillHotReloadWatcher started on {}", skillDir);
    }

    public void stop() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException e) {
            log.warn("Error closing WatchService", e);
        }
        debouncer.shutdown();
        log.info("SkillHotReloadWatcher stopped");
    }

    @Override
    public void close() {
        stop();
    }

    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running) {
                    log.warn("WatchService error", e);
                }
                break;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                Path filename = pathEvent.context();
                if (!filename.toString().endsWith(".md")) {
                    continue;
                }
                Path fullPath = skillDir.resolve(filename);
                scheduleReload(fullPath, kind);
            }
            key.reset();
        }
    }

    private void scheduleReload(Path path, WatchEvent.Kind<?> kind) {
        ScheduledFuture<?> existing = pending.remove(path);
        if (existing != null) {
            existing.cancel(false);
        }
        ScheduledFuture<?> future =
                debouncer.schedule(
                        () -> executeReload(path, kind), DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        pending.put(path, future);
    }

    private void executeReload(Path path, WatchEvent.Kind<?> kind) {
        pending.remove(path);
        String skillId = path.getFileName().toString().replaceFirst("\\.md$", "");

        if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            registry.unregister(skillId);
            fireEvent(
                    new SkillReloadEvent(
                            skillId, SkillReloadEvent.EventType.DELETED, Instant.now()));
        } else {
            SkillReloadEvent.EventType type =
                    kind == StandardWatchEventKinds.ENTRY_CREATE
                            ? SkillReloadEvent.EventType.CREATED
                            : SkillReloadEvent.EventType.UPDATED;
            loader.reloadFile(path)
                    .subscribe(
                            skill -> {
                                log.info("Hot-reloaded skill '{}' from {}", skill.name(), path);
                                fireEvent(new SkillReloadEvent(skill.name(), type, Instant.now()));
                            },
                            e ->
                                    log.error(
                                            "Failed to hot-reload skill from {}: {}",
                                            path,
                                            e.getMessage()));
        }
    }

    private void fireEvent(SkillReloadEvent event) {
        for (var listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.warn("Listener threw exception for event {}", event, e);
            }
        }
    }
}
