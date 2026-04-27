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
 * Concurrency and rate limits for a single tenant tier.
 *
 * @param maxConcurrent maximum simultaneous in-flight agent invocations per tenant
 * @param ratePerSecond sustained request rate in calls/second
 * @param burstCapacity maximum token accumulation before rate-limit kicks in
 */
public record TierBulkheadLimits(int maxConcurrent, double ratePerSecond, int burstCapacity) {

    /** Free tier: narrow concurrency, low throughput. */
    public static final TierBulkheadLimits FREE = new TierBulkheadLimits(5, 2.0, 5);

    /** Pro tier: moderate concurrency and throughput. */
    public static final TierBulkheadLimits PRO = new TierBulkheadLimits(20, 10.0, 20);

    /** Enterprise tier: broad concurrency, high throughput. */
    public static final TierBulkheadLimits ENTERPRISE = new TierBulkheadLimits(100, 50.0, 100);

    /** No limits — for single-tenant / trusted internal deployments. */
    public static final TierBulkheadLimits UNLIMITED =
            new TierBulkheadLimits(Integer.MAX_VALUE, Double.MAX_VALUE, Integer.MAX_VALUE);

    public TierBulkheadLimits {
        if (maxConcurrent < 1) throw new IllegalArgumentException("maxConcurrent >= 1");
        if (ratePerSecond <= 0) throw new IllegalArgumentException("ratePerSecond > 0");
        if (burstCapacity < 1) throw new IllegalArgumentException("burstCapacity >= 1");
    }

    /** Resolve a named tier string (case-insensitive) to its limits. */
    public static TierBulkheadLimits forTierName(String tier) {
        return switch (tier == null ? "free" : tier.toLowerCase()) {
            case "pro" -> PRO;
            case "enterprise" -> ENTERPRISE;
            case "unlimited" -> UNLIMITED;
            default -> FREE;
        };
    }
}
