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

import java.time.Duration;
import java.util.Optional;

/**
 * Storage backend for leader leases. Not part of the public kairo-api SPI.
 *
 * <p>Implementations must be thread-safe and provide compare-and-set semantics for {@link
 * #tryAcquire}.
 */
interface LeaderStore {

    /**
     * Attempt to acquire or renew the leader lease for the given node.
     *
     * @param nodeId the requesting node
     * @param leaseDuration how long the lease should last
     * @return {@code true} if the lease was acquired or renewed; {@code false} if another node
     *     holds a non-expired lease
     */
    boolean tryAcquire(String nodeId, Duration leaseDuration);

    /**
     * Release the lease held by the given node.
     *
     * <p>No-op if the node does not currently hold the lease.
     */
    void release(String nodeId);

    /** Returns the current lease, or empty if no node holds it (or it has expired). */
    Optional<LeaseEntry> currentLease();
}
