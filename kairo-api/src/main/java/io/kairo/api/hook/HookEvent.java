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
package io.kairo.api.hook;

import io.kairo.api.Experimental;

/**
 * Marker for any event that flows through the Kairo hook chain. Implementations remain their own
 * records (to avoid a massive sealed hierarchy migration) but declaring this marker lets cross-
 * cutting tooling — observability, auditing, replay — accept any hook event uniformly.
 *
 * <p>Not sealed on purpose: evolution, expert-team, and channel subsystems will contribute their
 * own {@code HookEvent} subtypes in later waves.
 *
 * @since v0.10 (Experimental)
 */
@Experimental("Unified hook dispatch — contract may change in v0.11")
public interface HookEvent {}
