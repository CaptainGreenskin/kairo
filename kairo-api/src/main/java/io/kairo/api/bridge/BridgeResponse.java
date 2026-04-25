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
package io.kairo.api.bridge;

import io.kairo.api.Stable;
import java.util.Map;

/**
 * Outbound envelope for a bridge call: agent runtime → client.
 *
 * <p>{@code status} follows HTTP semantics — {@code 2xx} success, {@code 4xx} client error, {@code
 * 5xx} server error. Bridge transports map this to their native error mechanism (WebSocket close
 * code, SSE error frame, etc).
 *
 * @param status response status; HTTP-style code (e.g. 200, 404, 500)
 * @param payload operation-specific result; defensively copied; never null (use {@link #empty(int)}
 *     when there is nothing to return)
 * @since v1.1
 */
@Stable(since = "1.1.0", value = "Bridge response envelope added in v1.1")
public record BridgeResponse(int status, Map<String, Object> payload) {

    public BridgeResponse {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    /** Common 200 OK response with the supplied payload. */
    public static BridgeResponse ok(Map<String, Object> payload) {
        return new BridgeResponse(200, payload);
    }

    /** 200 OK with an empty payload. */
    public static BridgeResponse ok() {
        return new BridgeResponse(200, Map.of());
    }

    /** Empty payload with the supplied status code. */
    public static BridgeResponse empty(int status) {
        return new BridgeResponse(status, Map.of());
    }

    /** {@code 404 Not Found} — the {@code op} has no registered handler. */
    public static BridgeResponse notFound(String op) {
        return new BridgeResponse(404, Map.of("op", op, "error", "operation-not-found"));
    }

    /** {@code 400 Bad Request} — malformed payload. */
    public static BridgeResponse badRequest(String reason) {
        return new BridgeResponse(400, Map.of("error", reason));
    }

    /** {@code 500 Internal Server Error} — handler threw / unexpected failure. */
    public static BridgeResponse internalError(String reason) {
        return new BridgeResponse(500, Map.of("error", reason));
    }
}
