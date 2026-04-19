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
package io.kairo.api.a2a;

import io.kairo.api.exception.KairoException;

/**
 * Exception thrown when an A2A (Agent-to-Agent) operation fails.
 *
 * <p>Typical causes include unknown target agent, invocation timeout, or transport failure.
 *
 * @see A2aClient
 */
public class A2aException extends KairoException {

    private final String targetAgentId;

    /**
     * Create a new A2A exception.
     *
     * @param targetAgentId the agent that was being invoked
     * @param message the detail message
     */
    public A2aException(String targetAgentId, String message) {
        super(message);
        this.targetAgentId = targetAgentId;
    }

    /**
     * Create a new A2A exception with a cause.
     *
     * @param targetAgentId the agent that was being invoked
     * @param message the detail message
     * @param cause the underlying cause
     */
    public A2aException(String targetAgentId, String message, Throwable cause) {
        super(message, cause);
        this.targetAgentId = targetAgentId;
    }

    /**
     * Returns the identifier of the target agent that caused this exception.
     *
     * @return the target agent ID
     */
    public String targetAgentId() {
        return targetAgentId;
    }
}
