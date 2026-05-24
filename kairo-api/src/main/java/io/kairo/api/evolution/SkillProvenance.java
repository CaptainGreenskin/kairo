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
package io.kairo.api.evolution;

import io.kairo.api.Experimental;

/**
 * Provenance of an evolved skill — where it came from. Drives curator eligibility: only {@link
 * #AGENT_CREATED} skills are subject to auto-lifecycle transitions and consolidation. Bundled and
 * hub-installed skills are off-limits to the curator (mirrors Hermes' provenance filter).
 *
 * @since v0.10 (Experimental)
 */
@Experimental("Self-Evolution SPI — contract may change before v1.2.0 stabilization")
public enum SkillProvenance {
    /** Auto-extracted by the evolution pipeline; the curator owns its lifecycle. */
    AGENT_CREATED,
    /** Shipped with the host application; curator must skip. */
    BUNDLED,
    /** Installed from a plugin / marketplace / external hub; curator must skip. */
    HUB,
    /** Hand-authored by a human operator; curator must skip. */
    MANUAL
}
