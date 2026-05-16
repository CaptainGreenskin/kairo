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
import javax.annotation.Nullable;

/**
 * Wire-format envelope for an A2A HTTP response.
 *
 * <p>JSON shape (success):
 *
 * <pre>{@code
 * {
 *   "status": "ok",
 *   "message": { ... Msg JSON ... }
 * }
 * }</pre>
 *
 * <p>JSON shape (error):
 *
 * <pre>{@code
 * {
 *   "status": "error",
 *   "errorCode": "AGENT_NOT_FOUND",
 *   "errorMessage": "No agent registered for id: xyz"
 * }
 * }</pre>
 */
public record A2aResponse(
        String status,
        @Nullable Msg message,
        @Nullable String errorCode,
        @Nullable String errorMessage) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public A2aResponse {
        Objects.requireNonNull(status, "status must not be null");
    }

    public static A2aResponse ok(Msg message) {
        return new A2aResponse("ok", message, null, null);
    }

    public static A2aResponse error(String errorCode, String errorMessage) {
        return new A2aResponse("error", null, errorCode, errorMessage);
    }

    public boolean isSuccess() {
        return "ok".equals(status);
    }

    public String toJson() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("status", status);
        if (message != null) {
            node.set("message", A2aMessageCodec.toJson(message));
        }
        if (errorCode != null) {
            node.put("errorCode", errorCode);
        }
        if (errorMessage != null) {
            node.put("errorMessage", errorMessage);
        }
        return node.toString();
    }

    public static A2aResponse fromJson(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            String st = node.path("status").asText("error");
            Msg msg = node.has("message") ? A2aMessageCodec.fromJson(node.path("message")) : null;
            String ec = node.path("errorCode").asText(null);
            String em = node.path("errorMessage").asText(null);
            return new A2aResponse(st, msg, ec, em);
        } catch (Exception e) {
            throw new A2aMessageCodec.A2aCodecException("Failed to decode A2aResponse", e);
        }
    }
}
