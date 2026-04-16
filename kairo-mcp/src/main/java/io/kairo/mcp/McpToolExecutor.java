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

import io.kairo.api.tool.ToolResult;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * Executes an MCP tool call via the MCP async client and converts the result to a Kairo {@link
 * ToolResult}.
 *
 * <p>This class bridges Kairo's tool execution model with the MCP protocol. It supports preset
 * arguments that are merged with each invocation's input (input takes precedence).
 */
public class McpToolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(McpToolExecutor.class);

    private final McpAsyncClient mcpClient;
    private final String mcpToolName;
    private final String kairoToolName;
    private final Map<String, Object> presetArgs;

    /**
     * Creates a new executor.
     *
     * @param mcpClient the MCP async client
     * @param mcpToolName the original MCP tool name (without server prefix)
     * @param kairoToolName the Kairo-registered tool name (with server prefix)
     * @param presetArgs preset arguments to merge with each call (may be null)
     */
    public McpToolExecutor(
            McpAsyncClient mcpClient,
            String mcpToolName,
            String kairoToolName,
            Map<String, Object> presetArgs) {
        this.mcpClient = mcpClient;
        this.mcpToolName = mcpToolName;
        this.kairoToolName = kairoToolName;
        this.presetArgs = presetArgs != null ? new HashMap<>(presetArgs) : null;
    }

    /**
     * Executes this MCP tool with the given input and returns a reactive result.
     *
     * @param input the input parameters from the LLM's tool-use request
     * @param toolUseId the correlation ID for this tool use
     * @return a Mono emitting the tool result
     */
    public Mono<ToolResult> execute(Map<String, Object> input, String toolUseId) {
        Map<String, Object> mergedArgs = mergeArguments(input);
        logger.debug(
                "Calling MCP tool '{}' (kairo: '{}') with args: {}",
                mcpToolName,
                kairoToolName,
                mergedArgs);

        return mcpClient
                .callTool(new McpSchema.CallToolRequest(mcpToolName, mergedArgs))
                .map(result -> McpContentConverter.convert(result, toolUseId))
                .doOnSuccess(r -> logger.debug("MCP tool '{}' completed", mcpToolName))
                .onErrorResume(
                        e -> {
                            logger.error(
                                    "Error calling MCP tool '{}': {}", mcpToolName, e.getMessage());
                            String errorMsg =
                                    e.getMessage() != null
                                            ? e.getMessage()
                                            : e.getClass().getSimpleName();
                            return Mono.just(
                                    new ToolResult(
                                            toolUseId,
                                            "MCP tool error: " + errorMsg,
                                            true,
                                            Collections.emptyMap()));
                        });
    }

    /**
     * Synchronous execution for use with Kairo's {@code ToolHandler} interface.
     *
     * @param input the input parameters
     * @return the tool result
     */
    public ToolResult executeSync(Map<String, Object> input) {
        return execute(input, "").block();
    }

    /** Returns the original MCP tool name. */
    public String getMcpToolName() {
        return mcpToolName;
    }

    /** Returns the Kairo-registered tool name. */
    public String getKairoToolName() {
        return kairoToolName;
    }

    private Map<String, Object> mergeArguments(Map<String, Object> input) {
        if (presetArgs == null || presetArgs.isEmpty()) {
            return input != null ? input : new HashMap<>();
        }
        Map<String, Object> merged = new HashMap<>(presetArgs);
        if (input != null) {
            merged.putAll(input);
        }
        return merged;
    }
}
