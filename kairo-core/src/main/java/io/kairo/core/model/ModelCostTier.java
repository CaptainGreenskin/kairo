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
package io.kairo.core.model;

/**
 * Quality/cost tier for model routing.
 *
 * <ul>
 *   <li>{@link #FREE} — cheapest / fastest (e.g. Haiku-class models)
 *   <li>{@link #STANDARD} — balanced cost and capability (e.g. Sonnet-class models)
 *   <li>{@link #PREMIUM} — highest capability (e.g. Opus-class models)
 * </ul>
 */
public enum ModelCostTier {
    FREE,
    STANDARD,
    PREMIUM
}
