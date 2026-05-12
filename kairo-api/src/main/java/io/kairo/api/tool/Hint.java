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
package io.kairo.api.tool;

import java.util.Optional;

/**
 * A hint attached to a tool result, providing actionable guidance to the agent or user.
 *
 * @param level the severity level of the hint
 * @param message human-readable message describing the hint
 * @param suggestedFix optional suggestion for how to address the issue
 * @since 1.2.0
 */
public record Hint(HintLevel level, String message, Optional<String> suggestedFix) {

    /** Severity level for hints. */
    public enum HintLevel {
        INFO,
        WARNING,
        ERROR
    }
}
