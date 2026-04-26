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
package io.kairo.core.agent;

import io.kairo.api.exception.KairoException;

/**
 * Thrown when the {@link LoopDetector} detects a repetitive tool call pattern that exceeds the
 * hard-stop threshold, indicating the agent is stuck in an infinite loop.
 */
public class LoopDetectionException extends KairoException {

    /**
     * Create a new LoopDetectionException with the given message.
     *
     * @param message a description of the detected loop pattern
     */
    public LoopDetectionException(String message) {
        super(message);
    }
}
