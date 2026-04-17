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
package io.kairo.spring;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Kairo MCP (Model Context Protocol) server connections.
 *
 * <p>Supports both HTTP and STDIO transport types via a discriminator pattern. Example YAML:
 *
 * <pre>{@code
 * kairo:
 *   mcp:
 *     servers:
 *       filesystem:
 *         transport: STDIO
 *         stdio:
 *           command: npx
 *           args: ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
 *       weather:
 *         transport: HTTP
 *         http:
 *           url: http://localhost:8080/mcp
 *           bearer-token: my-token
 * }</pre>
 */
@ConfigurationProperties(prefix = "kairo.mcp")
public class KairoMcpProperties {

    /** Named MCP server configurations. */
    private Map<String, McpServerProperties> servers = new LinkedHashMap<>();

    public Map<String, McpServerProperties> getServers() {
        return servers;
    }

    public void setServers(Map<String, McpServerProperties> servers) {
        this.servers = servers;
    }

    /** Configuration for a single MCP server. */
    public static class McpServerProperties {

        /** Transport type to use for this server. */
        private TransportType transport = TransportType.STDIO;

        /** HTTP transport configuration (used when transport=HTTP). */
        private HttpTransportProperties http;

        /** STDIO transport configuration (used when transport=STDIO). */
        private StdioTransportProperties stdio;

        public TransportType getTransport() {
            return transport;
        }

        public void setTransport(TransportType transport) {
            this.transport = transport;
        }

        public HttpTransportProperties getHttp() {
            return http;
        }

        public void setHttp(HttpTransportProperties http) {
            this.http = http;
        }

        public StdioTransportProperties getStdio() {
            return stdio;
        }

        public void setStdio(StdioTransportProperties stdio) {
            this.stdio = stdio;
        }
    }

    /** HTTP transport configuration. */
    public static class HttpTransportProperties {

        /** Server URL (e.g. http://localhost:8080/mcp). */
        private String url;

        /** HTTP headers to send with every request. */
        private Map<String, String> headers = new LinkedHashMap<>();

        /** Query parameters to append to the URL. */
        private Map<String, String> queryParams = new LinkedHashMap<>();

        /** Bearer token for authentication (convenience shorthand for Authorization header). */
        private String bearerToken;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }

        public Map<String, String> getQueryParams() {
            return queryParams;
        }

        public void setQueryParams(Map<String, String> queryParams) {
            this.queryParams = queryParams;
        }

        public String getBearerToken() {
            return bearerToken;
        }

        public void setBearerToken(String bearerToken) {
            this.bearerToken = bearerToken;
        }
    }

    /** STDIO transport configuration. */
    public static class StdioTransportProperties {

        /** Command to execute (e.g. "npx", "node", "python"). */
        private String command;

        /** Arguments to pass to the command. */
        private List<String> args = new ArrayList<>();

        /** Environment variables for the spawned process. */
        private Map<String, String> env = new LinkedHashMap<>();

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args;
        }

        public Map<String, String> getEnv() {
            return env;
        }

        public void setEnv(Map<String, String> env) {
            this.env = env;
        }
    }

    /** Supported MCP transport types. */
    public enum TransportType {
        HTTP,
        STDIO
    }
}
