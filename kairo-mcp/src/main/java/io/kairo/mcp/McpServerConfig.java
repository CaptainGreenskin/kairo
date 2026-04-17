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

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for connecting to an MCP server.
 *
 * @param name server name (used as tool name prefix)
 * @param transportType the transport type to use
 * @param command command list for stdio transport (e.g. ["npx", "-y", "server-package"])
 * @param url server URL for HTTP/SSE transports
 * @param env environment variables for stdio process
 * @param headers HTTP headers for HTTP/SSE transports
 * @param queryParams query parameters appended to the HTTP/SSE URL
 * @param bearerToken shorthand for setting an {@code Authorization: Bearer <token>} header
 * @param enableTools tool name whitelist (null = all tools enabled)
 * @param disableTools tool name blacklist
 * @param presetArgs preset arguments per tool name
 * @param requestTimeout per-request timeout (default 30s)
 */
public record McpServerConfig(
        String name,
        TransportType transportType,
        List<String> command,
        String url,
        Map<String, String> env,
        Map<String, String> headers,
        Map<String, String> queryParams,
        String bearerToken,
        List<String> enableTools,
        List<String> disableTools,
        Map<String, Map<String, Object>> presetArgs,
        Duration requestTimeout) {

    /** Supported MCP transport types. */
    public enum TransportType {
        STDIO,
        STREAMABLE_HTTP,
        SSE
    }

    /** Creates a stdio config with the given command. */
    public static McpServerConfig stdio(String name, String... command) {
        return new McpServerConfig(
                name,
                TransportType.STDIO,
                Arrays.asList(command),
                null,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                null,
                null,
                null,
                Collections.emptyMap(),
                Duration.ofSeconds(30));
    }

    /** Creates a Streamable HTTP config with the given URL. */
    public static McpServerConfig streamableHttp(String name, String url) {
        return new McpServerConfig(
                name,
                TransportType.STREAMABLE_HTTP,
                Collections.emptyList(),
                url,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                null,
                null,
                null,
                Collections.emptyMap(),
                Duration.ofSeconds(30));
    }

    /** Creates an SSE config with the given URL. */
    public static McpServerConfig sse(String name, String url) {
        return new McpServerConfig(
                name,
                TransportType.SSE,
                Collections.emptyList(),
                url,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                null,
                null,
                null,
                Collections.emptyMap(),
                Duration.ofSeconds(30));
    }

    /** Returns a new builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder for {@link McpServerConfig}. */
    public static class Builder {
        private String name;
        private TransportType transportType;
        private List<String> command = Collections.emptyList();
        private String url;
        private Map<String, String> env = new HashMap<>();
        private Map<String, String> headers = new HashMap<>();
        private Map<String, String> queryParams = new HashMap<>();
        private String bearerToken;
        private List<String> enableTools;
        private List<String> disableTools;
        private Map<String, Map<String, Object>> presetArgs = new HashMap<>();
        private Duration requestTimeout = Duration.ofSeconds(30);

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder transportType(TransportType transportType) {
            this.transportType = transportType;
            return this;
        }

        public Builder command(List<String> command) {
            this.command = command;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder env(Map<String, String> env) {
            this.env = new HashMap<>(env);
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = new HashMap<>(headers);
            return this;
        }

        public Builder queryParams(Map<String, String> queryParams) {
            this.queryParams = new HashMap<>(queryParams);
            return this;
        }

        /** Shorthand for setting an {@code Authorization: Bearer <token>} header. */
        public Builder bearerToken(String bearerToken) {
            this.bearerToken = bearerToken;
            return this;
        }

        public Builder enableTools(List<String> enableTools) {
            this.enableTools = enableTools;
            return this;
        }

        public Builder disableTools(List<String> disableTools) {
            this.disableTools = disableTools;
            return this;
        }

        public Builder presetArgs(Map<String, Map<String, Object>> presetArgs) {
            this.presetArgs = new HashMap<>(presetArgs);
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public McpServerConfig build() {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("Server name must not be null or blank");
            }
            if (transportType == null) {
                throw new IllegalArgumentException("Transport type must be specified");
            }
            return new McpServerConfig(
                    name,
                    transportType,
                    command,
                    url,
                    env,
                    headers,
                    queryParams,
                    bearerToken,
                    enableTools,
                    disableTools,
                    presetArgs,
                    requestTimeout);
        }
    }
}
