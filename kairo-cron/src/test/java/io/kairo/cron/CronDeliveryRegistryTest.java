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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.kairo.api.cron.CronDelivery;
import io.kairo.api.cron.CronTask;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Mono;

class CronDeliveryRegistryTest {

    @Test
    void registryRoutesByScheme(@TempDir Path tmp) {
        var calls = new ArrayList<String>();
        CronDelivery custom =
                new CronDelivery() {
                    @Override
                    public String scheme() {
                        return "channel";
                    }

                    @Override
                    public Mono<Void> deliver(CronTask task, String target, String content) {
                        calls.add("channel:" + target + " => " + content);
                        return Mono.empty();
                    }
                };
        var registry = new CronDeliveryRegistry().register(custom).register(new LogCronDelivery());
        CronTask task = new CronTask("abc", "* * * * *", "p", Instant.now(), null, true, false);

        registry.deliver(task, "channel:slack", "hello").block(Duration.ofSeconds(1));
        assertThat(calls).containsExactly("channel:slack => hello");
    }

    @Test
    void unknownSchemeErrors() {
        var task = new CronTask("x", "* * * * *", "p", Instant.now(), null, true, false);
        var registry = new CronDeliveryRegistry();
        assertThatThrownBy(
                        () ->
                                registry.deliver(task, "unknown:thing", "hi")
                                        .block(Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No CronDelivery");
    }

    @Test
    void fileDeliveryAppendsToTarget(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("nested/output.txt");
        var registry = new CronDeliveryRegistry().register(new FileCronDelivery());
        CronTask task = new CronTask("t-id", "* * * * *", "p", Instant.now(), null, true, false);

        registry.deliver(task, "file:" + out, "first").block(Duration.ofSeconds(1));
        registry.deliver(task, "file:" + out, "second").block(Duration.ofSeconds(1));

        String body = Files.readString(out);
        assertThat(body).contains("t-id").contains("first").contains("second");
        // Two lines.
        List<String> lines = Files.readAllLines(out);
        assertThat(lines).hasSize(2);
    }

    @Test
    void chainContextRoundTrips() {
        var ctx = new CronChainContext();
        ctx.recordOutput("upstream", "previous answer");
        assertThat(ctx.lastOutput("upstream")).contains("previous answer");
        assertThat(ctx.lastOutput("nonexistent")).isEmpty();
    }
}
