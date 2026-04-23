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
package io.kairo.api.execution;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link ExecutionStatus} enum coverage. */
class ExecutionStatusTest {

    @Test
    @DisplayName("all expected status values exist")
    void allValuesExist() {
        Set<String> names =
                Arrays.stream(ExecutionStatus.values()).map(Enum::name).collect(Collectors.toSet());

        assertTrue(names.contains("RUNNING"));
        assertTrue(names.contains("PAUSED"));
        assertTrue(names.contains("COMPLETED"));
        assertTrue(names.contains("FAILED"));
        assertTrue(names.contains("RECOVERING"));
    }

    @Test
    @DisplayName("valueOf round-trips for all statuses")
    void valueOfRoundTrip() {
        for (ExecutionStatus status : ExecutionStatus.values()) {
            assertEquals(status, ExecutionStatus.valueOf(status.name()));
        }
    }

    @Test
    @DisplayName("enum has exactly 5 values")
    void enumSize() {
        assertEquals(5, ExecutionStatus.values().length);
    }
}
