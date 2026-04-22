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

/**
 * The result of a {@link RoutingPolicy} provider selection.
 *
 * @param providerName the selected provider/model identifier
 * @param rationale a short description of why this provider was selected
 * @param estimatedCost optional estimated cost for this call (nullable)
 */
@Experimental("Cost Routing SPI — contract may change in v0.8")
public record RoutingDecision(String providerName, String rationale, Double estimatedCost) {}
