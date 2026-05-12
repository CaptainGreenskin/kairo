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
package io.kairo.core.agent.continuation;

import reactor.core.publisher.Mono;

/**
 * Default strategy that always terminates — preserves pre-0.5.0 behavior.
 *
 * <p>Used when continuation framework is disabled or no strategy is configured.
 *
 * @since 0.5.0
 */
public final class NoopContinuationStrategy implements AgentContinuationStrategy {

    /** Singleton instance. */
    public static final NoopContinuationStrategy INSTANCE = new NoopContinuationStrategy();

    private NoopContinuationStrategy() {}

    @Override
    public Mono<ContinuationDecision> decide(ContinuationContext ctx) {
        return Mono.just(new ContinuationDecision.Terminate("noop"));
    }

    @Override
    public String name() {
        return "Noop";
    }
}
