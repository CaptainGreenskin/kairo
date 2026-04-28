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
package io.kairo.core.tenant;

/**
 * Immutable configuration snapshot for a {@link TenantBulkhead}.
 *
 * @param maxConcurrency maximum simultaneous in-flight agent calls per tenant
 * @param requestsPerSecond token-bucket refill rate
 * @param burstCapacity maximum tokens that can accumulate
 */
public record TenantBulkheadConfig(
        int maxConcurrency, double requestsPerSecond, long burstCapacity) {

    public static final TenantBulkheadConfig DEFAULT = new TenantBulkheadConfig(10, 100.0, 200);

    public TenantBulkheadConfig {
        if (maxConcurrency < 1) throw new IllegalArgumentException("maxConcurrency must be >= 1");
        if (requestsPerSecond <= 0)
            throw new IllegalArgumentException("requestsPerSecond must be > 0");
        if (burstCapacity < 1) throw new IllegalArgumentException("burstCapacity must be >= 1");
    }

    public TenantBulkhead newBulkhead() {
        return new TenantBulkhead(maxConcurrency, requestsPerSecond, burstCapacity);
    }
}
