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
package io.kairo.core.leader;

import java.time.Instant;
import java.util.Objects;

/**
 * An immutable snapshot of a leader lease held by a node.
 *
 * @param nodeId unique identifier of the node holding the lease
 * @param acquiredAt when the lease was first acquired (or last refreshed for display)
 * @param expiresAt when the lease expires; after this instant it may be stolen
 */
public record LeaseEntry(String nodeId, Instant acquiredAt, Instant expiresAt) {

    public LeaseEntry {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(acquiredAt, "acquiredAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** Returns {@code true} if the lease has passed its expiry time. */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
