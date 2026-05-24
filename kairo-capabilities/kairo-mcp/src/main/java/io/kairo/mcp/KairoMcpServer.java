package io.kairo.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolOutput;
import io.kairo.api.tool.ToolResult;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class KairoMcpServer {

    private static final Logger log = LoggerFactory.getLogger(KairoMcpServer.class);

    private final String serverName;
    private final String serverVersion;
    private final ToolExecutor toolExecutor;
    private final List<ToolDefinition> tools;
    private final Set<String> allowedTools;
    private volatile McpAsyncServer server;
    private volatile McpServerTransportProvider transportProvider;

    private KairoMcpServer(Builder builder) {
        this.serverName = builder.serverName;
        this.serverVersion = builder.serverVersion;
        this.toolExecutor = builder.toolExecutor;
        this.tools = filterTools(builder.tools, builder.allowedTools);
        this.allowedTools = builder.allowedTools;
    }

    public Mono<Void> startStdio() {
        transportProvider =
                new StdioServerTransportProvider(new JacksonMcpJsonMapper(new ObjectMapper()));
        return start(transportProvider);
    }

    public Mono<Void> start(McpServerTransportProvider provider) {
        this.transportProvider = provider;

        var spec =
                McpServer.async(provider)
                        .serverInfo(new McpSchema.Implementation(serverName, serverVersion))
                        .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build());

        for (ToolDefinition tool : tools) {
            McpSchema.Tool mcpTool = toMcpTool(tool);
            spec =
                    spec.toolCall(
                            mcpTool,
                            (exchange, request) -> {
                                Map<String, Object> args =
                                        request.arguments() != null
                                                ? request.arguments()
                                                : Map.of();
                                return toolExecutor
                                        .execute(tool.name(), args)
                                        .map(
                                                result -> {
                                                    String text = extractText(result);
                                                    boolean isError =
                                                            result.outcome()
                                                                    == io.kairo.api.tool.ToolOutcome
                                                                            .ERROR;
                                                    List<McpSchema.Content> content =
                                                            List.of(
                                                                    new McpSchema.TextContent(
                                                                            text));
                                                    return new McpSchema.CallToolResult(
                                                            content, isError, null, null);
                                                })
                                        .onErrorResume(
                                                e -> {
                                                    log.warn(
                                                            "Tool execution failed for {}: {}",
                                                            tool.name(),
                                                            e.getMessage());
                                                    List<McpSchema.Content> content =
                                                            List.of(
                                                                    new McpSchema.TextContent(
                                                                            "Error: "
                                                                                    + e
                                                                                            .getMessage()));
                                                    return Mono.just(
                                                            new McpSchema.CallToolResult(
                                                                    content, true, null, null));
                                                });
                            });
        }

        server = spec.build();
        log.info(
                "MCP server '{}' started with {} tools via {}",
                serverName,
                tools.size(),
                provider.getClass().getSimpleName());
        return Mono.empty();
    }

    public Mono<Void> stop() {
        if (server != null) {
            return Mono.fromRunnable(
                    () -> {
                        try {
                            server.closeGracefully().block();
                            log.info("MCP server '{}' stopped", serverName);
                        } catch (Exception e) {
                            log.warn("Error stopping MCP server: {}", e.getMessage());
                        }
                    });
        }
        return Mono.empty();
    }

    public boolean isRunning() {
        return server != null;
    }

    public List<ToolDefinition> exposedTools() {
        return Collections.unmodifiableList(tools);
    }

    private static String extractText(ToolResult result) {
        if (result.output() instanceof ToolOutput.Text text) {
            return text.content() != null ? text.content() : "";
        }
        return result.output() != null ? result.output().toString() : "";
    }

    private static McpSchema.Tool toMcpTool(ToolDefinition tool) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = List.of();
        JsonSchema inputSchema = tool.inputSchema();
        if (inputSchema != null && inputSchema.properties() != null) {
            for (var entry : inputSchema.properties().entrySet()) {
                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put(
                        "type",
                        entry.getValue().type() != null ? entry.getValue().type() : "string");
                if (entry.getValue().description() != null) {
                    prop.put("description", entry.getValue().description());
                }
                properties.put(entry.getKey(), prop);
            }
            if (inputSchema.required() != null && !inputSchema.required().isEmpty()) {
                required = inputSchema.required();
            }
        }
        McpSchema.JsonSchema mcpSchema =
                new McpSchema.JsonSchema("object", properties, required, null, null, null);
        return McpSchema.Tool.builder()
                .name(tool.name())
                .description(tool.description())
                .inputSchema(mcpSchema)
                .build();
    }

    private static List<ToolDefinition> filterTools(
            List<ToolDefinition> tools, Set<String> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            return new ArrayList<>(tools);
        }
        List<ToolDefinition> filtered = new ArrayList<>();
        for (ToolDefinition tool : tools) {
            if (allowed.contains(tool.name())) {
                filtered.add(tool);
            }
        }
        return filtered;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String serverName = "kairo-code";
        private String serverVersion = "0.2.0";
        private ToolExecutor toolExecutor;
        private List<ToolDefinition> tools = List.of();
        private Set<String> allowedTools;

        public Builder serverName(String name) {
            this.serverName = name;
            return this;
        }

        public Builder serverVersion(String version) {
            this.serverVersion = version;
            return this;
        }

        public Builder toolExecutor(ToolExecutor executor) {
            this.toolExecutor = executor;
            return this;
        }

        public Builder tools(List<ToolDefinition> tools) {
            this.tools = tools;
            return this;
        }

        public Builder allowedTools(Set<String> allowed) {
            this.allowedTools = allowed;
            return this;
        }

        public KairoMcpServer build() {
            if (toolExecutor == null) {
                throw new IllegalStateException("ToolExecutor is required");
            }
            return new KairoMcpServer(this);
        }
    }
}
