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
package io.kairo.api.routing;

import io.kairo.api.Experimental;
import reactor.core.publisher.Mono;

/**
 * SPI for selecting a model provider based on routing context.
 *
 * <p>Implementations can consider cost budgets, message complexity, and other factors to choose the
 * most appropriate provider for a given request.
 */
@Experimental("Cost Routing SPI — contract may change in v0.8")
public interface RoutingPolicy {

    /**
     * Select a provider for the given routing context.
     *
     * @param context the routing context containing agent info, messages, and configuration
     * @return a Mono emitting the routing decision
     */
    Mono<RoutingDecision> selectProvider(RoutingContext context);
}
