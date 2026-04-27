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
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-process {@link LeaderStore} using an {@link AtomicReference} for CAS semantics.
 *
 * <p>Suitable for single-JVM deployments or testing. A multi-node deployment must replace this with
 * a shared backend (JDBC, Redis, etc.).
 */
public final class InMemoryLeaderStore implements LeaderStore {

    private final AtomicReference<LeaseEntry> lease = new AtomicReference<>(null);

    @Override
    public boolean tryAcquire(String nodeId, Duration leaseDuration) {
        Instant now = Instant.now();
        Instant newExpiry = now.plus(leaseDuration);

        while (true) {
            LeaseEntry current = lease.get();

            // No lease or expired → anyone can take it
            if (current == null || current.isExpired()) {
                LeaseEntry newLease = new LeaseEntry(nodeId, now, newExpiry);
                if (lease.compareAndSet(current, newLease)) {
                    return true;
                }
                // Another thread updated concurrently — retry
                continue;
            }

            // This node already holds the lease — renew it
            if (current.nodeId().equals(nodeId)) {
                LeaseEntry renewed = new LeaseEntry(nodeId, current.acquiredAt(), newExpiry);
                if (lease.compareAndSet(current, renewed)) {
                    return true;
                }
                continue;
            }

            // Another node holds a valid lease
            return false;
        }
    }

    @Override
    public void release(String nodeId) {
        while (true) {
            LeaseEntry current = lease.get();
            if (current == null || !current.nodeId().equals(nodeId)) {
                return; // nothing to release
            }
            if (lease.compareAndSet(current, null)) {
                return;
            }
        }
    }

    @Override
    public Optional<LeaseEntry> currentLease() {
        LeaseEntry current = lease.get();
        if (current == null || current.isExpired()) return Optional.empty();
        return Optional.of(current);
    }
}
