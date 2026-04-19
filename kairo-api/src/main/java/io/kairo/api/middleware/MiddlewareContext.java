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
package io.kairo.api.middleware;

import io.kairo.api.message.Msg;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Request context flowing through the middleware pipeline.
 *
 * <p>Carries the agent's input message, conversation history, and a mutable {@link #attributes} map
 * for cross-middleware data exchange. The attributes map is {@link LinkedHashMap}-backed to
 * preserve insertion order for debugging, and is shared-mutable by design within a single reactive
 * subscriber chain (no concurrent access).
 *
 * @param agentName the target agent's name
 * @param sessionId the session identifier, may be null
 * @param input the user's input message
 * @param conversationHistory prior messages in this session, may be empty
 * @param attributes mutable cross-middleware data; never null
 */
public record MiddlewareContext(
        String agentName,
        String sessionId,
        Msg input,
        List<Msg> conversationHistory,
        Map<String, Object> attributes) {

    /** Create a minimal context with no history and empty attributes. */
    public static MiddlewareContext of(String agentName, Msg input) {
        return new MiddlewareContext(agentName, null, input, List.of(), new LinkedHashMap<>());
    }

    /** Create a context with session info. */
    public static MiddlewareContext of(String agentName, String sessionId, Msg input) {
        return new MiddlewareContext(agentName, sessionId, input, List.of(), new LinkedHashMap<>());
    }

    /** Create a context with full conversation history. */
    public static MiddlewareContext of(
            String agentName, String sessionId, Msg input, List<Msg> conversationHistory) {
        return new MiddlewareContext(
                agentName, sessionId, input, conversationHistory, new LinkedHashMap<>());
    }

    /**
     * Return a new context with an additional attribute.
     *
     * <p>Modifies the attributes map in-place for efficiency within a single reactive chain.
     */
    public MiddlewareContext withAttribute(String key, Object value) {
        attributes.put(key, value);
        return this;
    }

    /** Return a new context with all attributes from the given map added. */
    public MiddlewareContext withAttributes(Map<String, Object> additional) {
        attributes.putAll(additional);
        return this;
    }

    /** Return an unmodifiable view of the attributes. */
    public Map<String, Object> attributes() {
        return Collections.unmodifiableMap(attributes);
    }
}
