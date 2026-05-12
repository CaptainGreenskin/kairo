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
package io.kairo.core.tool.approval;

import io.kairo.api.tool.ApprovalGate;
import reactor.core.publisher.Mono;

/**
 * Safe default {@link ApprovalGate} that rejects every request.
 *
 * <p>Use in headless / unattended environments where there is no human to ask. Pairs naturally with
 * conservative tool sets — dangerous operations simply do not run. Apps that want to opt out of
 * gating entirely should wire {@link AutoApproveGate} instead.
 */
public final class DenyGate implements ApprovalGate {

    private static final String DEFAULT_FEEDBACK =
            "No interactive approval gate is configured; rejecting by default.";

    @Override
    public Mono<Decision> await(String description, String reason) {
        return Mono.just(Rejected.with(DEFAULT_FEEDBACK));
    }
}
