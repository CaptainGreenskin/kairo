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
package io.kairo.spring.execution;

/**
 * Thrown when an optimistic locking conflict is detected during a durable execution status update.
 *
 * @since v0.8
 */
public class OptimisticLockException extends RuntimeException {

    private final String executionId;
    private final int expectedVersion;

    public OptimisticLockException(String executionId, int expectedVersion) {
        super(
                "Optimistic lock failed for execution "
                        + executionId
                        + " at version "
                        + expectedVersion);
        this.executionId = executionId;
        this.expectedVersion = expectedVersion;
    }

    public String getExecutionId() {
        return executionId;
    }

    public int getExpectedVersion() {
        return expectedVersion;
    }
}
