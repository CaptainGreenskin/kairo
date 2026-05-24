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
 * Lifecycle state of an evolved skill, driven by the {@code CuratorDaemon} from observed activity.
 *
 * <p>Transitions are unidirectional under typical curator runs ({@link #ACTIVE} → {@link #STALE} →
 * {@link #ARCHIVED}); a skill that gets used again after being marked {@link #STALE} is reactivated
 * back to {@link #ACTIVE}. {@link #ARCHIVED} skills are not auto-promoted — reactivation requires
 * an explicit manual call (mirrors Hermes' archive recoverability semantics).
 *
 * @since v0.10 (Experimental)
 */
@Experimental("Self-Evolution SPI — contract may change before v1.2.0 stabilization")
public enum SkillLifecycleState {
    /** In active use; eligible for prompt injection and discovery. */
    ACTIVE,
    /** Unused beyond the stale threshold; still discoverable but flagged for review. */
    STALE,
    /** Removed from normal discovery, recoverable via explicit unarchive. */
    ARCHIVED
}
