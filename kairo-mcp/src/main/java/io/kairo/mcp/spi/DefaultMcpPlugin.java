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
package io.kairo.mcp.spi;

import io.kairo.api.mcp.McpPlugin;
import io.kairo.api.mcp.McpPluginRegistration;
import io.kairo.api.mcp.McpPluginTool;
import io.kairo.mcp.McpClientRegistry;
import io.kairo.mcp.McpServerConfig;
import io.kairo.mcp.McpToolGroup;
import java.util.List;
import reactor.core.publisher.Mono;

/** Default MCP plugin backed by {@link McpClientRegistry}. */
public class DefaultMcpPlugin implements McpPlugin {

    private final McpClientRegistry registry = new McpClientRegistry();

    @Override
    public boolean supports(Object serverConfig) {
        return serverConfig instanceof McpServerConfig;
    }

    @Override
    public Mono<McpPluginRegistration> register(Object serverConfig) {
        if (!(serverConfig instanceof McpServerConfig cfg)) {
            return Mono.error(
                    new IllegalArgumentException(
                            "Unsupported MCP server config type: "
                                    + (serverConfig == null
                                            ? "null"
                                            : serverConfig.getClass().getName())));
        }
        return registry.register(cfg).map(this::toRegistration);
    }

    private McpPluginRegistration toRegistration(McpToolGroup group) {
        List<McpPluginTool> tools =
                group.getRegisteredToolNames().stream()
                        .map(
                                name ->
                                        new McpPluginTool(
                                                group.getToolDefinition(name),
                                                group.getExecutor(name)))
                        .toList();
        return new McpPluginRegistration(group.getServerName(), tools);
    }

    @Override
    public void close() {
        registry.close();
    }
}
