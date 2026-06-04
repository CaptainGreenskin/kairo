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
package io.kairo.api.team;

import io.kairo.api.Experimental;

/**
 * Lifecycle status of a team entity.
 *
 * <p>Distinct from {@link TeamStatus} which represents terminal execution result states. This enum
 * tracks the live lifecycle of the team from creation through dissolution.
 */
@Experimental("Team lifecycle status; introduced in v1.2.0")
public enum TeamLifecycleStatus {
    INITIALIZING,
    ACTIVE,
    COMPLETED,
    FAILED,
    DISSOLVED
}
