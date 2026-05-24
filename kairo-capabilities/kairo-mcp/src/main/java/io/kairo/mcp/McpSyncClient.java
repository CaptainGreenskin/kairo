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

import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Blocking wrapper around the MCP async client.
 *
 * <p>Provides a synchronous API for users who prefer blocking calls over reactive Mono-based APIs.
 *
 * <p><strong>WARNING:</strong> Do NOT use in reactive pipelines. These methods call {@code
 * .block()} internally and will deadlock in reactive contexts (e.g. inside a Reactor scheduler).
 *
 * <p>Example:
 *
 * <pre>{@code
 * McpSyncClient client = McpClientBuilder.create("weather")
 *     .stdioTransport("npx", "-y", "@modelcontextprotocol/server-weather")
 *     .buildSync();
 * client.initialize();
 * List<McpSchema.Tool> tools = client.listTools();
 * }</pre>
 */
public class McpSyncClient implements AutoCloseable {

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final McpAsyncClient delegate;
    private final Duration defaultTimeout;

    /**
     * Creates a sync client wrapping the given async client with the default timeout of 30 seconds.
     *
     * @param delegate the async MCP client to wrap
     */
    public McpSyncClient(McpAsyncClient delegate) {
        this(delegate, DEFAULT_TIMEOUT);
    }

    /**
     * Creates a sync client wrapping the given async client with a custom timeout.
     *
     * @param delegate the async MCP client to wrap
     * @param defaultTimeout the maximum duration to block on each call
     */
    public McpSyncClient(McpAsyncClient delegate, Duration defaultTimeout) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        if (defaultTimeout == null) {
            throw new IllegalArgumentException("defaultTimeout must not be null");
        }
        this.delegate = delegate;
        this.defaultTimeout = defaultTimeout;
    }

    /**
     * Returns the underlying async client.
     *
     * @return the wrapped {@link McpAsyncClient}
     */
    public McpAsyncClient getDelegate() {
        return delegate;
    }

    /**
     * Returns the default timeout used for blocking calls.
     *
     * @return the default timeout duration
     */
    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    /**
     * Initializes the MCP connection synchronously.
     *
     * @return the initialization result
     */
    public McpSchema.InitializeResult initialize() {
        return delegate.initialize().block(defaultTimeout);
    }

    /**
     * Sends a ping to the MCP server and waits for the response.
     *
     * @return the ping result
     */
    public Object ping() {
        return delegate.ping().block(defaultTimeout);
    }

    /**
     * Lists available tools from the MCP server.
     *
     * @return the list of tools
     */
    public List<McpSchema.Tool> listTools() {
        McpSchema.ListToolsResult result = delegate.listTools().block(defaultTimeout);
        return result != null ? result.tools() : List.of();
    }

    /**
     * Calls a tool on the MCP server and waits for the result.
     *
     * @param name the tool name
     * @param arguments the tool arguments
     * @return the call tool result
     */
    public McpSchema.CallToolResult callTool(String name, Map<String, Object> arguments) {
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(name, arguments);
        return delegate.callTool(request).block(defaultTimeout);
    }

    /**
     * Lists available resources from the MCP server.
     *
     * @return the list of resources
     */
    public List<McpSchema.Resource> listResources() {
        McpSchema.ListResourcesResult result = delegate.listResources().block(defaultTimeout);
        return result != null ? result.resources() : List.of();
    }

    /**
     * Reads a resource by URI from the MCP server.
     *
     * @param uri the resource URI
     * @return the read resource result
     */
    public McpSchema.ReadResourceResult readResource(String uri) {
        McpSchema.ReadResourceRequest request = new McpSchema.ReadResourceRequest(uri);
        return delegate.readResource(request).block(defaultTimeout);
    }

    /**
     * Lists available prompts from the MCP server.
     *
     * @return the list of prompts
     */
    public List<McpSchema.Prompt> listPrompts() {
        McpSchema.ListPromptsResult result = delegate.listPrompts().block(defaultTimeout);
        return result != null ? result.prompts() : List.of();
    }

    /** Closes the underlying MCP connection. */
    @Override
    public void close() {
        delegate.close();
    }

    /** Closes the underlying MCP connection gracefully, waiting for pending operations. */
    public void closeGracefully() {
        delegate.closeGracefully().block(defaultTimeout);
    }
}
