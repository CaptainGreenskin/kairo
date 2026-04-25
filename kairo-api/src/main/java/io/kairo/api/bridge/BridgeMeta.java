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
package io.kairo.api.bridge;

import io.kairo.api.Stable;
import io.kairo.api.tenant.TenantContext;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Cross-cutting metadata for a {@link BridgeRequest}.
 *
 * <p>Carries the correlation id used to match a {@link BridgeResponse} back to its caller, the
 * active {@link TenantContext} for attribution / audit, the request origin timestamp, and free-form
 * attributes (session id, principal name, client version, etc).
 *
 * <p>Authentication is <b>not</b> the bridge's responsibility — Spring Security or the equivalent
 * sits in front of the transport. {@code BridgeMeta} simply forwards what the upstream auth layer
 * resolved.
 *
 * @param requestId stable correlation id; if blank, the bridge transport may synthesize one and
 *     overwrite it before reaching the handler
 * @param tenant active tenant context; never null — defaults to {@link TenantContext#SINGLE}
 * @param timestamp when the bridge transport observed the inbound frame
 * @param attributes free-form metadata (session id, principal, etc); never null, defensively copied
 * @since v1.1
 */
@Stable(since = "1.1.0", value = "Bridge metadata envelope added in v1.1")
public record BridgeMeta(
        String requestId, TenantContext tenant, Instant timestamp, Map<String, String> attributes) {

    public BridgeMeta {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(tenant, "tenant");
        Objects.requireNonNull(timestamp, "timestamp");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** Generates a fresh {@code BridgeMeta} with a UUID request id and the supplied tenant. */
    public static BridgeMeta create(TenantContext tenant) {
        return new BridgeMeta(UUID.randomUUID().toString(), tenant, Instant.now(), Map.of());
    }

    /** Generates a fresh {@code BridgeMeta} with {@link TenantContext#SINGLE}. */
    public static BridgeMeta create() {
        return create(TenantContext.SINGLE);
    }
}
