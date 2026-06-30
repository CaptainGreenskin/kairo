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
package io.kairo.multiagent.orchestration;

import io.kairo.api.team.SharedContext;
import io.kairo.api.team.TeamExecutionRequest;
import reactor.core.publisher.Mono;

/**
 * SPI for gathering workspace context before expert team step execution.
 *
 * <p>The orchestrator invokes this once per team execution to collect shared workspace metadata
 * (file tree, key files, project summary). The result is then injected into each agent's prompt
 * based on its role's {@code ContextScope} requirements, eliminating redundant tool calls.
 *
 * <p>Implementations should be idempotent and safe for concurrent invocation.
 *
 * @since v0.11 (Experimental)
 */
@FunctionalInterface
public interface WorkspaceContextGatherer {

    /**
     * Gather workspace context for the given execution request.
     *
     * @param request the team execution request providing goal and context hints
     * @return a Mono emitting the gathered SharedContext
     */
    Mono<SharedContext> gather(TeamExecutionRequest request);
}
