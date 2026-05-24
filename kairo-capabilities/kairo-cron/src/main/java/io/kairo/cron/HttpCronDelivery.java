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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * {@link CronDelivery} that POSTs the task's output to a target URL. Schema: {@code http} / {@code
 * https} (the registry chops the {@code http:} prefix off, so the actual target string passed here
 * is e.g. {@code "//webhook.example.com/cron"} which we re-wrap with {@code https}).
 *
 * <p>Headers can be carried via an alternative target syntax {@code "//host/path|H1=v1|H2=v2"}. The
 * pipe-separated trailing tokens are parsed into {@code Header=Value} pairs. Bearer-token
 * convenience: pass {@code "Authorization=Bearer xyz"} as one of the tokens.
 *
 * <p>10-second connect timeout, 30-second request timeout. Non-2xx responses surface as {@code
 * Mono.error}.
 */
public final class HttpCronDelivery implements CronDelivery {

    private static final Logger log = LoggerFactory.getLogger(HttpCronDelivery.class);

    private final HttpClient client;
    private final String scheme;

    /** Registered under the {@code http} scheme. */
    public HttpCronDelivery() {
        this("http");
    }

    public HttpCronDelivery(String scheme) {
        this.scheme = scheme;
        this.client =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .build();
    }

    /** Drop-in factory for HTTPS routing — registers under the {@code https} scheme. */
    public static HttpCronDelivery https() {
        return new HttpCronDelivery("https");
    }

    @Override
    public String scheme() {
        return scheme;
    }

    @Override
    public Mono<Void> deliver(CronTask task, String target, String content) {
        return Mono.fromCallable(() -> build(target, content))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(
                        req ->
                                Mono.fromFuture(
                                        client.sendAsync(
                                                req, HttpResponse.BodyHandlers.discarding())))
                .doOnSuccess(
                        resp -> {
                            if (resp.statusCode() / 100 != 2) {
                                throw new IllegalStateException(
                                        "HTTP "
                                                + resp.statusCode()
                                                + " from cron delivery to "
                                                + resp.uri());
                            }
                            log.debug(
                                    "HttpCronDelivery: task {} → {} → {}",
                                    task.id(),
                                    resp.uri(),
                                    resp.statusCode());
                        })
                .then();
    }

    private HttpRequest build(String target, String body) {
        // Target arrives stripped of the scheme prefix (the registry split on the first colon).
        // We need to re-wrap with the scheme to get a valid URI.
        String full = scheme + ":" + target;
        // Optional pipe-separated header pairs after the URL.
        Map<String, String> headers = new HashMap<>();
        String url = full;
        int firstPipe = full.indexOf('|');
        if (firstPipe >= 0) {
            url = full.substring(0, firstPipe);
            for (String pair : full.substring(firstPipe + 1).split("\\|")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    headers.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                }
            }
        }
        HttpRequest.Builder b =
                HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "text/plain; charset=utf-8")
                        .header("User-Agent", "kairo-cron")
                        .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        headers.forEach(b::header);
        return b.build();
    }
}
