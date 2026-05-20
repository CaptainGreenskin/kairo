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

import io.kairo.api.cron.CronTask;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpCronDeliveryTest {

    private MockWebServer server;
    private CronDeliveryRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        registry = new CronDeliveryRegistry().register(new HttpCronDelivery());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void postsBodyToTargetUrl() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        CronTask task = new CronTask("t-id", "* * * * *", "p", Instant.now(), null, true, false);
        String target = server.url("/webhook").toString();
        registry.deliver(task, target, "hello world").block(Duration.ofSeconds(5));

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/webhook");
        assertThat(req.getBody().readUtf8()).isEqualTo("hello world");
        assertThat(req.getHeader("User-Agent")).isEqualTo("kairo-cron");
    }

    @Test
    void customHeadersAfterPipeSeparator() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(202));
        CronTask task = new CronTask("t-id", "* * * * *", "p", Instant.now(), null, true, false);
        String target =
                server.url("/hook").toString() + "|Authorization=Bearer xyz|X-Cron-Source=kairo";
        registry.deliver(task, target, "ok").block(Duration.ofSeconds(5));

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer xyz");
        assertThat(req.getHeader("X-Cron-Source")).isEqualTo("kairo");
    }

    @Test
    void nonTwoXxStatusSurfacesAsError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));
        CronTask task = new CronTask("t-id", "* * * * *", "p", Instant.now(), null, true, false);
        String target = server.url("/hook").toString();
        assertThatThrownBy(
                        () -> registry.deliver(task, target, "boom").block(Duration.ofSeconds(5)))
                .hasMessageContaining("HTTP 500");
    }
}
