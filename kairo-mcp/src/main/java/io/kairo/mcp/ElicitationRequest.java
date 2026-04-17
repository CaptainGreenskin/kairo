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
package io.kairo.mcp;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Wrapper around the MCP SDK's {@code ElicitRequest}, providing a user-friendly API without leaking
 * SDK internals.
 *
 * <p>Contains the server's message to the user and an optional JSON Schema describing the requested
 * input format.
 *
 * @see ElicitationHandler
 */
public final class ElicitationRequest {

    private final String message;
    private final Map<String, Object> requestedSchema;

    /**
     * Creates a new elicitation request.
     *
     * @param message the server's message describing what input is needed
     * @param requestedSchema a JSON Schema describing the expected input structure, or empty map if
     *     no specific schema is required
     */
    public ElicitationRequest(String message, Map<String, Object> requestedSchema) {
        this.message = Objects.requireNonNull(message, "message must not be null");
        this.requestedSchema =
                requestedSchema != null
                        ? Collections.unmodifiableMap(requestedSchema)
                        : Collections.emptyMap();
    }

    /**
     * Returns the server's message describing what input is needed.
     *
     * @return the elicitation message
     */
    public String message() {
        return message;
    }

    /**
     * Returns the JSON Schema describing the expected input structure.
     *
     * @return an unmodifiable map representing the requested schema, never null
     */
    public Map<String, Object> requestedSchema() {
        return requestedSchema;
    }

    @Override
    public String toString() {
        return "ElicitationRequest{message='" + message + "', requestedSchema=" + requestedSchema + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ElicitationRequest that)) return false;
        return Objects.equals(message, that.message)
                && Objects.equals(requestedSchema, that.requestedSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, requestedSchema);
    }
}
