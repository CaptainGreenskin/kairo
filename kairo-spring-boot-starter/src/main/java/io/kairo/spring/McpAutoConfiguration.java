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

import io.kairo.mcp.AutoApproveElicitationHandler;
import io.kairo.mcp.ElicitationHandler;
import io.kairo.mcp.McpClientRegistry;
import io.kairo.mcp.McpServerConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Kairo MCP (Model Context Protocol) support.
 *
 * <p>Only activates when {@code kairo-mcp} is on the classpath (detected via {@link
 * McpClientRegistry}). Reads MCP server definitions from {@link KairoMcpProperties} and creates a
 * {@link McpClientRegistry} bean.
 */
@AutoConfiguration
@ConditionalOnClass(McpClientRegistry.class)
@EnableConfigurationProperties(KairoMcpProperties.class)
public class McpAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ElicitationHandler defaultElicitationHandler() {
        return new AutoApproveElicitationHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public McpClientRegistry mcpClientRegistry(KairoMcpProperties properties) {
        McpClientRegistry registry = new McpClientRegistry();

        for (Map.Entry<String, KairoMcpProperties.McpServerProperties> entry :
                properties.getServers().entrySet()) {
            String serverName = entry.getKey();
            KairoMcpProperties.McpServerProperties serverProps = entry.getValue();

            McpServerConfig config = toServerConfig(serverName, serverProps);
            log.info(
                    "Registering MCP server '{}' (transport={})",
                    serverName,
                    serverProps.getTransport());
            registry.register(config).block();
        }

        return registry;
    }

    private McpServerConfig toServerConfig(
            String name, KairoMcpProperties.McpServerProperties props) {
        McpServerConfig.Builder builder = McpServerConfig.builder().name(name);

        switch (props.getTransport()) {
            case HTTP -> {
                KairoMcpProperties.HttpTransportProperties http = props.getHttp();
                if (http == null || http.getUrl() == null || http.getUrl().isBlank()) {
                    throw new IllegalArgumentException(
                            "MCP server '"
                                    + name
                                    + "' uses HTTP transport but no URL is configured");
                }
                builder.transportType(McpServerConfig.TransportType.STREAMABLE_HTTP);
                builder.url(http.getUrl());

                Map<String, String> headers = new HashMap<>(http.getHeaders());
                if (http.getBearerToken() != null && !http.getBearerToken().isBlank()) {
                    headers.put("Authorization", "Bearer " + http.getBearerToken());
                }
                builder.headers(headers);
            }
            case STDIO -> {
                KairoMcpProperties.StdioTransportProperties stdio = props.getStdio();
                if (stdio == null || stdio.getCommand() == null || stdio.getCommand().isBlank()) {
                    throw new IllegalArgumentException(
                            "MCP server '"
                                    + name
                                    + "' uses STDIO transport but no command is configured");
                }
                builder.transportType(McpServerConfig.TransportType.STDIO);

                List<String> command = new ArrayList<>();
                command.add(stdio.getCommand());
                command.addAll(stdio.getArgs());
                builder.command(command);

                if (!stdio.getEnv().isEmpty()) {
                    builder.env(stdio.getEnv());
                }
            }
        }

        return builder.build();
    }
}
