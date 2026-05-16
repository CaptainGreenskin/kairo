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
package io.kairo.core.a2a.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kairo.api.message.Msg;
import java.util.Objects;

/**
 * Wire-format envelope for an A2A HTTP request.
 *
 * <p>JSON shape:
 *
 * <pre>{@code
 * {
 *   "targetAgentId": "agent-1",
 *   "message": { ... Msg JSON ... },
 *   "streaming": false
 * }
 * }</pre>
 *
 * @param targetAgentId the remote agent to invoke
 * @param message the input message
 * @param streaming whether to request SSE streaming
 */
public record A2aRequest(String targetAgentId, Msg message, boolean streaming) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public A2aRequest {
        Objects.requireNonNull(targetAgentId, "targetAgentId must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    public String toJson() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("targetAgentId", targetAgentId);
        node.set("message", A2aMessageCodec.toJson(message));
        node.put("streaming", streaming);
        return node.toString();
    }

    public static A2aRequest fromJson(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            String targetId = node.path("targetAgentId").asText();
            Msg msg = A2aMessageCodec.fromJson(node.path("message"));
            boolean stream = node.path("streaming").asBoolean(false);
            return new A2aRequest(targetId, msg, stream);
        } catch (Exception e) {
            throw new A2aMessageCodec.A2aCodecException("Failed to decode A2aRequest", e);
        }
    }
}
