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
 * Wrapper around the MCP SDK's {@code ElicitResult}, providing a user-friendly API without leaking
 * SDK internals.
 *
 * <p>Contains the user's decision ({@link ElicitationAction}) and any data provided in response to
 * the elicitation request.
 *
 * @see ElicitationHandler
 * @see ElicitationAction
 */
public final class ElicitationResponse {

    private final ElicitationAction action;
    private final Map<String, Object> data;

    /**
     * Creates a new elicitation response.
     *
     * @param action the user's decision
     * @param data the data provided by the user, or empty map if none
     */
    public ElicitationResponse(ElicitationAction action, Map<String, Object> data) {
        this.action = Objects.requireNonNull(action, "action must not be null");
        this.data =
                data != null ? Collections.unmodifiableMap(data) : Collections.emptyMap();
    }

    /**
     * Creates an ACCEPT response with the given data.
     *
     * @param data the data provided by the user
     * @return an accept response
     */
    public static ElicitationResponse accept(Map<String, Object> data) {
        return new ElicitationResponse(ElicitationAction.ACCEPT, data);
    }

    /**
     * Creates a DECLINE response with no data.
     *
     * @return a decline response
     */
    public static ElicitationResponse decline() {
        return new ElicitationResponse(ElicitationAction.DECLINE, Map.of());
    }

    /**
     * Creates a CANCEL response with no data.
     *
     * @return a cancel response
     */
    public static ElicitationResponse cancel() {
        return new ElicitationResponse(ElicitationAction.CANCEL, Map.of());
    }

    /**
     * Returns the user's decision.
     *
     * @return the elicitation action
     */
    public ElicitationAction action() {
        return action;
    }

    /**
     * Returns the data provided by the user.
     *
     * @return an unmodifiable map of user-provided data, never null
     */
    public Map<String, Object> data() {
        return data;
    }

    @Override
    public String toString() {
        return "ElicitationResponse{action=" + action + ", data=" + data + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ElicitationResponse that)) return false;
        return action == that.action && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, data);
    }
}
