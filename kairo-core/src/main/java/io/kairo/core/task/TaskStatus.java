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
package io.kairo.core.task;

/** Task lifecycle status. */
public enum TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED;

    public static TaskStatus fromString(String s) {
        return switch (s.strip().toLowerCase()) {
            case "pending" -> PENDING;
            case "in_progress" -> IN_PROGRESS;
            case "completed" -> COMPLETED;
            default -> throw new IllegalArgumentException("Unknown task status: " + s);
        };
    }

    public String toJson() {
        return name().toLowerCase();
    }
}
