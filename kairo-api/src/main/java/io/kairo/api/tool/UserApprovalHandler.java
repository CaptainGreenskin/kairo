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
 * Handler for requesting user approval before executing tools with significant side effects.
 *
 * @apiNote Stable SPI — backward compatible across minor versions. Breaking changes only in major
 *     versions with 2-minor-version deprecation notice.
 * @implSpec Implementations may block waiting for user input (e.g., CLI prompt, UI dialog). The
 *     returned {@link reactor.core.publisher.Mono} should complete when the user responds.
 *     Implementations must handle timeout scenarios gracefully — returning {@link
 *     ApprovalResult#denied()} is preferred over hanging indefinitely.
 * @since 0.4.0
 */
public interface UserApprovalHandler {

    /**
     * Request user approval for the given tool call.
     *
     * @param request the tool call request requiring approval
     * @return a Mono emitting the approval result
     */
    Mono<ApprovalResult> requestApproval(ToolCallRequest request);
}
