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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.plugin.PluginSource;
import io.kairo.plugin.installer.PluginCacheManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Fetches a plugin published as an npm package.
 *
 * <p>Resolution flow:
 *
 * <ol>
 *   <li>GET {@code https://registry.npmjs.org/&lt;package&gt;} — JSON metadata
 *   <li>Pick the {@code versions[&lt;version&gt;]} entry (or resolve a {@code dist-tag} via {@code
 *       tags} field on the source)
 *   <li>Download the tarball from {@code dist.tarball}
 *   <li>Verify the SHA-1 {@code dist.shasum} matches what we received (npm always ships shasum)
 *   <li>Extract with {@code stripComponents=1} — npm tarballs wrap content in {@code package/}
 * </ol>
 *
 * <p>The configurable registry base URL defaults to {@code https://registry.npmjs.org}; private
 * registries can be addressed by overriding it. Authentication tokens are not yet supported — they
 * will arrive alongside CLI work in Phase D.
 */
public final class NpmSourceFetcher implements PluginSourceFetcher {

    private static final Logger log = LoggerFactory.getLogger(NpmSourceFetcher.class);
    private static final String DEFAULT_REGISTRY = "https://registry.npmjs.org";

    private final PluginCacheManager cache;
    private final HttpDownloader http;
    private final String registryBaseUrl;
    private final ObjectMapper json = new ObjectMapper();

    public NpmSourceFetcher(PluginCacheManager cache, HttpDownloader http) {
        this(cache, http, DEFAULT_REGISTRY);
    }

    public NpmSourceFetcher(PluginCacheManager cache, HttpDownloader http, String registryBaseUrl) {
        this.cache = cache;
        this.http = http;
        this.registryBaseUrl =
                registryBaseUrl.endsWith("/")
                        ? registryBaseUrl.substring(0, registryBaseUrl.length() - 1)
                        : registryBaseUrl;
    }

    @Override
    public boolean supports(PluginSource source) {
        return source instanceof PluginSource.Npm;
    }

    @Override
    public String kind() {
        return "npm";
    }

    @Override
    public Mono<Path> fetch(PluginSource source) {
        if (!(source instanceof PluginSource.Npm npm)) {
            return Mono.error(new IllegalArgumentException("Not an Npm source: " + source));
        }
        return Mono.fromCallable(() -> doFetch(npm)).subscribeOn(Schedulers.boundedElastic());
    }

    private Path doFetch(PluginSource.Npm npm) throws IOException, InterruptedException {
        if (npm.packageName() == null || npm.packageName().isBlank()) {
            throw new IllegalArgumentException("Npm source requires non-blank packageName");
        }
        if (npm.version() == null || npm.version().isBlank()) {
            throw new IllegalArgumentException(
                    "Npm source requires non-blank version (no dist-tag resolution yet)");
        }

        String identity = identityKey(npm);
        Path slot = cache.slotFor(kind(), identity);

        if (cache.isPopulated(kind(), identity)) {
            log.debug("Npm source cache hit for {}", identity);
            return slot;
        }
        java.nio.file.Files.createDirectories(slot);

        // Step 1: fetch package metadata.
        String metaUrl = registryBaseUrl + "/" + encodePackageName(npm.packageName());
        log.info("Fetching npm metadata: {}", metaUrl);
        JsonNode metadata;
        try (InputStream meta = http.get(metaUrl)) {
            metadata = json.readTree(meta);
        }
        JsonNode versionNode = metadata.path("versions").path(npm.version());
        if (versionNode.isMissingNode() || versionNode.isNull()) {
            throw new IOException(
                    "npm version not found: " + npm.packageName() + "@" + npm.version());
        }
        String tarballUrl = versionNode.path("dist").path("tarball").asText(null);
        if (tarballUrl == null || tarballUrl.isBlank()) {
            throw new IOException(
                    "npm metadata missing dist.tarball for "
                            + npm.packageName()
                            + "@"
                            + npm.version());
        }
        String expectedShasum = versionNode.path("dist").path("shasum").asText(null);

        // Step 2: download tarball + verify shasum.
        log.info("Fetching npm tarball: {}", tarballUrl);
        byte[] tarball;
        try (InputStream body = http.get(tarballUrl)) {
            tarball = body.readAllBytes();
        }
        if (expectedShasum != null && !expectedShasum.isBlank()) {
            String actual = sha1Hex(tarball);
            if (!actual.equalsIgnoreCase(expectedShasum)) {
                throw new IOException(
                        "npm tarball SHA-1 mismatch for "
                                + npm.packageName()
                                + "@"
                                + npm.version()
                                + ": expected="
                                + expectedShasum
                                + " actual="
                                + actual);
            }
        }

        // Step 3: extract. npm tarballs wrap content in "package/" — stripComponents=1.
        TarGzExtractor.extract(new ByteArrayInputStream(tarball), slot, 1);
        return slot;
    }

    static String identityKey(PluginSource.Npm npm) {
        return npm.packageName() + "@" + npm.version();
    }

    /**
     * URL-encodes a package name. Scoped names like {@code @scope/pkg} get the slash escaped to
     * {@code %2f} per npm registry conventions.
     */
    static String encodePackageName(String name) {
        if (name.startsWith("@") && name.contains("/")) {
            int slash = name.indexOf('/');
            return URLEncoder.encode(name.substring(0, slash), StandardCharsets.UTF_8)
                    + "%2F"
                    + URLEncoder.encode(name.substring(slash + 1), StandardCharsets.UTF_8);
        }
        return URLEncoder.encode(name, StandardCharsets.UTF_8);
    }

    private static String sha1Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }
}
