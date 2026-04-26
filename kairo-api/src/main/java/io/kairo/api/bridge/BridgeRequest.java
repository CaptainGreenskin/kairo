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
import java.util.Map;
import java.util.Objects;

/**
 * Inbound envelope for a bridge call: client →agent runtime.
 *
 * <p>Schemaless by design — {@code op} addresses the operation, {@code payload} carries arguments
 * as a map (JSON-mappable), and {@code meta} carries cross-cutting context (correlation id, tenant,
 * timestamp). New operations can be added without changing this record; the bridge protocol freezes
 * <i>shape</i>, not the {@code op} table.
 *
 * @param op stable operation name (dot-namespaced, e.g. {@code agent.run}); never null/blank
 * @param payload operation-specific arguments; defensively copied to an immutable view
 * @param meta cross-cutting metadata; never null
 * @since v1.1
 */
@Stable(since = "1.1.0", value = "Bridge request envelope added in v1.1")
public record BridgeRequest(String op, Map<String, Object> payload, BridgeMeta meta) {

    public BridgeRequest {
        Objects.requireNonNull(op, "op");
        if (op.isBlank()) {
            throw new IllegalArgumentException("op must not be blank");
        }
        Objects.requireNonNull(meta, "meta");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    /** Convenience constructor with empty payload. */
    public BridgeRequest(String op, BridgeMeta meta) {
        this(op, Map.of(), meta);
    }
}
