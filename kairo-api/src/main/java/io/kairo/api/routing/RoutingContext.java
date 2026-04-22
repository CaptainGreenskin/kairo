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
import io.kairo.api.message.Msg;
import io.kairo.api.model.ModelConfig;
import java.util.List;

/**
 * Context provided to a {@link RoutingPolicy} for provider selection.
 *
 * @param agentName the name of the agent requesting the model call
 * @param messages the conversation messages so far
 * @param config the model configuration for this call
 * @param costBudget optional cost budget limit (nullable, from ModelConfig)
 */
@Experimental("Cost Routing SPI — contract may change in v0.8")
public record RoutingContext(
        String agentName, List<Msg> messages, ModelConfig config, Double costBudget) {}
