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
package io.kairo.core.lsp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

final class JsonRpcMessageTest {

    @Test
    void requestWithParams() {
        ObjectNode params = JsonRpcMessage.MAPPER.createObjectNode();
        params.put("rootUri", "file:///workspace");

        String json = JsonRpcMessage.request(1, "initialize", params);

        assertThat(json).contains("\"jsonrpc\":\"2.0\"");
        assertThat(json).contains("\"id\":1");
        assertThat(json).contains("\"method\":\"initialize\"");
        assertThat(json).contains("\"rootUri\":\"file:///workspace\"");
    }

    @Test
    void requestWithoutParams() {
        String json = JsonRpcMessage.request(42, "shutdown", null);

        assertThat(json).contains("\"id\":42");
        assertThat(json).contains("\"method\":\"shutdown\"");
        assertThat(json).doesNotContain("\"params\"");
    }

    @Test
    void notification() {
        String json = JsonRpcMessage.notification("initialized", null);

        assertThat(json).contains("\"method\":\"initialized\"");
        assertThat(json).doesNotContain("\"id\"");
    }

    @Test
    void parseResultSuccess() {
        String json = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"capabilities\":{}}}";
        JsonNode result = JsonRpcMessage.parseResult(json);

        assertThat(result.has("capabilities")).isTrue();
    }

    @Test
    void parseResultError() {
        String json =
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601,\"message\":\"Method not found\"}}";

        assertThatThrownBy(() -> JsonRpcMessage.parseResult(json))
                .isInstanceOf(LspException.class)
                .hasMessageContaining("-32601")
                .hasMessageContaining("Method not found");
    }

    @Test
    void parseResultInvalidJson() {
        assertThatThrownBy(() -> JsonRpcMessage.parseResult("not json"))
                .isInstanceOf(LspException.class);
    }
}
