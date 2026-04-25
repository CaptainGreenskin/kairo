/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */

/**
 * TenantContext SPI — passive multi-tenant identity propagation.
 *
 * <p>Goal: let every cross-cutting concern (audit, quota, log, metric, cost) aggregate by tenant
 * without forcing each call site to thread a tenant id through method signatures.
 *
 * <p>Design rules (ADR-027):
 *
 * <ul>
 *   <li><b>Passive</b> — the context is read by sinks (event bus, OTel exporter, security sink),
 *       never required by call sites.
 *   <li><b>Backward compatible</b> — when nothing binds a tenant, {@link
 *       io.kairo.api.tenant.TenantContext#SINGLE} is observed; existing single-tenant deployments
 *       see no behavior change.
 *   <li><b>Out of scope for v1.1</b> — quota enforcement, physical isolation, cross-tenant routing.
 *       Those land in v1.2 (per {@code .plans/V1.2-DISTRIBUTED.md} D1).
 * </ul>
 *
 * @since v1.1
 */
package io.kairo.api.tenant;
