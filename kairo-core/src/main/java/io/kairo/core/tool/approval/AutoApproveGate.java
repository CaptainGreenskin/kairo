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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * {@link ApprovalGate} that approves every request without prompting.
 *
 * <p>Intended for CI / sandboxed / trusted-input environments where every dangerous action has
 * already been gated upstream and the agent is allowed to proceed unattended. Each call logs at
 * WARN so accidental production wiring is easy to spot in logs.
 */
public final class AutoApproveGate implements ApprovalGate {

    private static final Logger log = LoggerFactory.getLogger(AutoApproveGate.class);

    @Override
    public Mono<Decision> await(String description, String reason) {
        log.warn("AutoApproveGate approving without prompt: description={} reason={}",
                description, reason);
        return Mono.just(Approved.asIs());
    }
}
