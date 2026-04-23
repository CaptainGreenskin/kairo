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

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kairo.api.task.Plan;
import io.kairo.api.task.Task;
import io.kairo.api.task.TaskBoard;
import io.kairo.api.task.TaskStatus;
import org.junit.jupiter.api.Test;

/**
 * Fails if any of the legacy Task / TaskBoard / TeamScheduler types lose their {@code @Deprecated}
 * marker before the v0.11 removal wave. This is a cheap guardrail; it ensures that new code
 * considering using these types sees the retirement intent at compile-time.
 */
class LegacyTeamApiRetirementTest {

    @Test
    void legacyTypesAreMarkedForRemoval() {
        assertTrue(Task.class.isAnnotationPresent(Deprecated.class));
        assertTrue(Task.class.getAnnotation(Deprecated.class).forRemoval());

        assertTrue(TaskBoard.class.isAnnotationPresent(Deprecated.class));
        assertTrue(TaskBoard.class.getAnnotation(Deprecated.class).forRemoval());

        assertTrue(TaskStatus.class.isAnnotationPresent(Deprecated.class));
        assertTrue(TaskStatus.class.getAnnotation(Deprecated.class).forRemoval());

        assertTrue(Plan.class.isAnnotationPresent(Deprecated.class));
        assertTrue(Plan.class.getAnnotation(Deprecated.class).forRemoval());

        assertTrue(TeamScheduler.class.isAnnotationPresent(Deprecated.class));
        assertTrue(TeamScheduler.class.getAnnotation(Deprecated.class).forRemoval());
    }
}
