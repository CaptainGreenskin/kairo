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
package io.kairo.core.routing;

import io.kairo.api.routing.RoutingContext;
import io.kairo.api.routing.RoutingDecision;
import io.kairo.api.routing.RoutingPolicy;
import reactor.core.publisher.Mono;

/**
 * Default no-op routing policy that always returns the currently configured provider.
 *
 * <p>This is a placeholder implementation with no cost-based routing logic. It simply returns the
 * model from the provided configuration as the selected provider.
 *
 * <p>{@link io.kairo.api.routing.RoutingDecision#estimatedCost() estimatedCost} returns {@code
 * null} by design — no cost estimation is performed.
 *
 * <p>This implementation will be replaced by a cost-aware routing policy in v0.8.
 */
public class DefaultRoutingPolicy implements RoutingPolicy {

    @Override
    public Mono<RoutingDecision> selectProvider(RoutingContext context) {
        // No-op: return the currently configured provider
        String currentProvider = context.config().model();
        return Mono.just(new RoutingDecision(currentProvider, "default-no-routing", null));
    }
}
