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
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watches a directory for changes to skill files and triggers hot-reload.
 *
 * <p>Uses the Java NIO {@link WatchService} to monitor {@code .md} files. On each change the
 * affected file is reloaded via {@link SkillLoader} and re-registered in {@link SkillRegistry}.
 */
public final class SkillHotReloadWatcher {

    private static final Logger log = LoggerFactory.getLogger(SkillHotReloadWatcher.class);

    private final Path watchDir;
    private final SkillLoader skillLoader;
    private final SkillRegistry skillRegistry;
    private volatile boolean running;
    private Thread watchThread;

    public SkillHotReloadWatcher(
            Path watchDir, SkillLoader skillLoader, SkillRegistry skillRegistry) {
        this.watchDir = watchDir;
        this.skillLoader = skillLoader;
        this.skillRegistry = skillRegistry;
    }

    public void start() throws IOException {
        if (running) return;
        running = true;
        watchThread = new Thread(this::watchLoop, "kairo-skill-hot-reload");
        watchThread.setDaemon(true);
        watchThread.start();
        log.info("SkillHotReloadWatcher started for: {}", watchDir);
    }

    public void stop() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
        log.info("SkillHotReloadWatcher stopped");
    }

    private void watchLoop() {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            watchDir.register(
                    watchService,
                    java.nio.file.StandardWatchEventKinds.ENTRY_CREATE,
                    java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY,
                    java.nio.file.StandardWatchEventKinds.ENTRY_DELETE);

            while (running) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == java.nio.file.StandardWatchEventKinds.OVERFLOW) continue;

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path file = watchDir.resolve(ev.context());
                    if (!file.toString().endsWith(".md")) continue;

                    log.debug("Skill file changed: {}", file);
                    try {
                        skillLoader.reloadFile(file).subscribe();
                    } catch (Exception e) {
                        log.warn("Failed to reload skill file {}: {}", file, e.getMessage());
                    }
                }
                key.reset();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            log.error("WatchService error for {}", watchDir, e);
        }
    }
}
