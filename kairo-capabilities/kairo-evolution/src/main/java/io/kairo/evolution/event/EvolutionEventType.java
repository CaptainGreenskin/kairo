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
package io.kairo.evolution.event;

/**
 * Types of evolution lifecycle events emitted by the evolution pipeline.
 *
 * @since v0.9 (Experimental)
 */
public enum EvolutionEventType {
    SKILL_CREATED,
    SKILL_UPDATED,
    SKILL_QUARANTINED,
    SKILL_SCAN_PASSED,
    SKILL_SCAN_REJECTED,
    SKILL_ACTIVATED,
    EVOLUTION_SUSPENDED,
    EVOLUTION_RESUMED
}
