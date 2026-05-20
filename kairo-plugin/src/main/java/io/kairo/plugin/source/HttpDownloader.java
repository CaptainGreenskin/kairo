/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.source;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Tiny abstraction over Java {@link HttpClient} for streaming downloads. Exists primarily so tests
 * can inject a fake — the real fetchers route GitHub / npm tarball downloads through this.
 */
public interface HttpDownloader {

    /**
     * Streams the body of a GET request. Throws {@link IOException} on non-2xx status.
     *
     * @param url the URL to fetch
     * @return open InputStream — caller must close it
     */
    InputStream get(String url) throws IOException, InterruptedException;

    /** Default JDK-based downloader; redirects followed; 60s connect timeout. */
    static HttpDownloader jdk() {
        return new JdkHttpDownloader();
    }

    /** Default implementation. */
    final class JdkHttpDownloader implements HttpDownloader {
        private final HttpClient client =
                HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .connectTimeout(Duration.ofSeconds(60))
                        .build();

        @Override
        public InputStream get(String url) throws IOException, InterruptedException {
            HttpRequest req =
                    HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofMinutes(5))
                            .header("Accept", "application/octet-stream")
                            .header("User-Agent", "kairo-plugin")
                            .build();
            HttpResponse<InputStream> resp =
                    client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() / 100 != 2) {
                resp.body().close();
                throw new IOException("HTTP " + resp.statusCode() + " fetching " + url);
            }
            return resp.body();
        }
    }
}
