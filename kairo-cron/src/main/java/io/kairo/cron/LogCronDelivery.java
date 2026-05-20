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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/** {@link CronDelivery} that writes the task's output to slf4j at INFO. Schema: {@code log}. */
public final class LogCronDelivery implements CronDelivery {

    private static final Logger log = LoggerFactory.getLogger("io.kairo.cron.output");

    @Override
    public String scheme() {
        return "log";
    }

    @Override
    public Mono<Void> deliver(CronTask task, String target, String content) {
        return Mono.fromRunnable(
                () -> log.info("Cron task {} fired [target={}]: {}", task.id(), target, content));
    }
}
