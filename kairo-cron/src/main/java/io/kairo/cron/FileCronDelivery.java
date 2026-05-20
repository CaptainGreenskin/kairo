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

import io.kairo.api.cron.CronDelivery;
import io.kairo.api.cron.CronTask;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * {@link CronDelivery} that appends each output to a file. Schema: {@code file}; target is the
 * absolute path. Lines are prefixed with the task id + ISO timestamp so multiple tasks can share
 * the same file without losing attribution.
 */
public final class FileCronDelivery implements CronDelivery {

    private static final Logger log = LoggerFactory.getLogger(FileCronDelivery.class);

    @Override
    public String scheme() {
        return "file";
    }

    @Override
    public Mono<Void> deliver(CronTask task, String target, String content) {
        return Mono.fromRunnable(
                () -> {
                    if (target == null || target.isBlank()) {
                        log.warn(
                                "FileCronDelivery: empty target for task {} — skipping", task.id());
                        return;
                    }
                    Path p = Path.of(target);
                    try {
                        if (p.getParent() != null) Files.createDirectories(p.getParent());
                        String line =
                                "["
                                        + java.time.Instant.now()
                                        + " "
                                        + task.id()
                                        + "] "
                                        + (content == null ? "" : content)
                                        + System.lineSeparator();
                        Files.writeString(
                                p,
                                line,
                                StandardCharsets.UTF_8,
                                new OpenOption[] {
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.WRITE,
                                    StandardOpenOption.APPEND
                                });
                    } catch (IOException e) {
                        log.warn(
                                "FileCronDelivery: failed to append to {} for task {}: {}",
                                p,
                                task.id(),
                                e.getMessage());
                    }
                });
    }
}
