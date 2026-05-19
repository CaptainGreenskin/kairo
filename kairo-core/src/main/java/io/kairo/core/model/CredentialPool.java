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
package io.kairo.core.model;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Round-robin credential pool with rate-limit tracking and automatic failover.
 *
 * <p>When a key is rate-limited, it is skipped until the cooldown expires. If all keys are limited,
 * the least-restricted key is returned as a last resort.
 *
 * <p>Thread-safe. Designed for multi-key API access with high-throughput rotation.
 *
 * @since 0.9
 */
public class CredentialPool {

    private static final Logger log = LoggerFactory.getLogger(CredentialPool.class);

    private final CopyOnWriteArrayList<Credential> credentials;
    private final AtomicInteger index = new AtomicInteger(0);
    private final int failThreshold;

    public CredentialPool(List<String> apiKeys) {
        this(apiKeys, 3);
    }

    public CredentialPool(List<String> apiKeys, int failThreshold) {
        this.credentials =
                new CopyOnWriteArrayList<>(apiKeys.stream().map(Credential::new).toList());
        this.failThreshold = failThreshold;
        log.info("Credential pool initialized with {} keys", credentials.size());
    }

    /**
     * Get the next available API key via round-robin, skipping rate-limited keys.
     *
     * @return an available API key
     * @throws IllegalStateException if the pool is empty
     */
    public String next() {
        if (credentials.isEmpty()) {
            throw new IllegalStateException("No credentials available in pool");
        }

        int attempts = credentials.size();
        for (int i = 0; i < attempts; i++) {
            int idx = Math.floorMod(index.getAndIncrement(), credentials.size());
            Credential cred = credentials.get(idx);
            if (!cred.isRateLimited()) {
                return cred.key;
            }
        }

        Credential oldest =
                credentials.stream()
                        .filter(Credential::isRateLimited)
                        .min((a, b) -> a.rateLimitedUntil.compareTo(b.rateLimitedUntil))
                        .orElse(credentials.get(0));
        log.warn("All keys rate-limited, returning least-restricted key");
        return oldest.key;
    }

    /** Mark a key as rate-limited for the specified duration. */
    public void markRateLimited(String key, int retryAfterSeconds) {
        for (Credential cred : credentials) {
            if (cred.key.equals(key)) {
                cred.rateLimitedUntil = Instant.now().plusSeconds(retryAfterSeconds);
                log.info("Key ...{} rate-limited for {}s", mask(key), retryAfterSeconds);
                break;
            }
        }
    }

    /** Record a failure. After reaching the threshold, the key is temporarily disabled. */
    public void markFailed(String key) {
        for (Credential cred : credentials) {
            if (cred.key.equals(key)) {
                cred.failCount++;
                if (cred.failCount >= failThreshold) {
                    cred.rateLimitedUntil = Instant.now().plusSeconds(300);
                    log.warn(
                            "Key ...{} disabled for 5min after {} failures",
                            mask(key),
                            cred.failCount);
                }
                break;
            }
        }
    }

    /** Record a successful use, resetting the failure counter. */
    public void markSuccess(String key) {
        for (Credential cred : credentials) {
            if (cred.key.equals(key)) {
                cred.failCount = 0;
                break;
            }
        }
    }

    public int size() {
        return credentials.size();
    }

    public int availableCount() {
        return (int) credentials.stream().filter(c -> !c.isRateLimited()).count();
    }

    private static String mask(String key) {
        return key.length() > 4 ? key.substring(key.length() - 4) : "****";
    }

    private static class Credential {
        final String key;
        volatile Instant rateLimitedUntil = Instant.EPOCH;
        volatile int failCount = 0;

        Credential(String key) {
            this.key = key;
        }

        boolean isRateLimited() {
            return Instant.now().isBefore(rateLimitedUntil);
        }
    }
}
