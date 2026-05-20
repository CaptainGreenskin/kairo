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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import reactor.core.publisher.Mono;

/**
 * Routes a (target, content) pair to the right {@link CronDelivery} implementation, keyed by the
 * URI-style scheme prefix (the substring before the first {@code :}). {@code register} returns the
 * registry for fluent setup so hosts can wire several backends in one place.
 *
 * <p>Lookup is exact-match on the scheme. {@code "origin"} (no prefix / no colon) is also supported
 * as a sentinel for "send back to the conversation that scheduled the task" — hosts that don't
 * expose conversations can register a delivery under the {@code "origin"} scheme to handle it
 * however they like.
 */
public final class CronDeliveryRegistry {

    private final Map<String, CronDelivery> bySchema = new LinkedHashMap<>();

    public CronDeliveryRegistry register(CronDelivery delivery) {
        Objects.requireNonNull(delivery, "delivery");
        Objects.requireNonNull(delivery.scheme(), "delivery.scheme()");
        bySchema.put(delivery.scheme(), delivery);
        return this;
    }

    public Optional<CronDelivery> resolve(String target) {
        if (target == null || target.isBlank()) return Optional.empty();
        int colon = target.indexOf(':');
        String scheme = colon < 0 ? target : target.substring(0, colon);
        return Optional.ofNullable(bySchema.get(scheme));
    }

    /**
     * Convenience: split {@code target} on the first {@code :} into (scheme, rest), look up the
     * delivery, and call it with {@code rest} as the target argument. If no delivery is registered
     * for the scheme, returns a {@code Mono.error}.
     */
    public Mono<Void> deliver(CronTask task, String target, String content) {
        Optional<CronDelivery> match = resolve(target);
        if (match.isEmpty()) {
            return Mono.error(
                    new IllegalStateException(
                            "No CronDelivery registered for target scheme in '" + target + "'"));
        }
        int colon = target.indexOf(':');
        String inner = colon < 0 ? "" : target.substring(colon + 1);
        return match.get().deliver(task, inner, content);
    }

    public Map<String, CronDelivery> snapshot() {
        return Map.copyOf(bySchema);
    }
}
