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

/**
 * Facade for leader election with lease-based exclusion.
 *
 * <p>A node calls {@link #tryAcquire} to attempt leadership. If successful, it should periodically
 * call {@link #refresh} to renew its lease before expiry. Call {@link #release} to voluntarily
 * relinquish leadership.
 */
public interface LeaderElector {

    /**
     * Attempt to acquire or renew leadership.
     *
     * @return {@code true} if this node is now the leader
     */
    boolean tryAcquire();

    /** Renew the lease if this node is already the leader. Equivalent to {@link #tryAcquire}. */
    default boolean refresh() {
        return tryAcquire();
    }

    /** Release leadership voluntarily. No-op if this node is not the current leader. */
    void release();

    /** Returns {@code true} if this node currently holds a valid (non-expired) lease. */
    boolean isLeader();
}
