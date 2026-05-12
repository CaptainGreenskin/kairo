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
package io.kairo.api.tool;

import reactor.core.publisher.Mono;

/**
 * SPI for gating tool execution behind human approval.
 *
 * <p>Implementations may prompt the user via CLI, UI, or external approval systems. The framework
 * suspends tool execution until the returned {@link Mono} completes with a {@link Decision}.
 *
 * @since 1.2.0
 */
public interface ApprovalGate {

    /**
     * Await approval for a tool action.
     *
     * @param description what the tool wants to do
     * @param reason why approval is needed
     * @return a Mono emitting the approval decision
     */
    Mono<Decision> await(String description, String reason);

    /** Approval decision. */
    enum Decision {
        APPROVED,
        REJECTED
    }
}
