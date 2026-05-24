package io.kairo.mcp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import io.kairo.api.tool.ToolExecutor;
import io.kairo.api.tool.ToolResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class KairoMcpServerTest {

    private ToolExecutor mockExecutor() {
        ToolExecutor executor = mock(ToolExecutor.class);
        when(executor.execute(eq("echo"), anyMap()))
                .thenReturn(Mono.just(ToolResult.success("1", "hello")));
        when(executor.execute(eq("fail"), anyMap()))
                .thenReturn(Mono.just(ToolResult.error("2", "boom")));
        return executor;
    }

    private ToolDefinition tool(String name, String desc) {
        return new ToolDefinition(name, desc, ToolCategory.GENERAL, null, Object.class);
    }

    private ToolDefinition toolWithSchema(String name) {
        JsonSchema schema =
                new JsonSchema(
                        "object",
                        Map.of("input", new JsonSchema("string", null, null, "The input value")),
                        List.of("input"),
                        null);
        return new ToolDefinition(
                name, "Tool with schema", ToolCategory.GENERAL, schema, Object.class);
    }

    @Test
    void buildRequiresToolExecutor() {
        assertThrows(IllegalStateException.class, () -> KairoMcpServer.builder().build());
    }

    @Test
    void buildWithMinimalConfig() {
        KairoMcpServer server = KairoMcpServer.builder().toolExecutor(mockExecutor()).build();
        assertFalse(server.isRunning());
        assertTrue(server.exposedTools().isEmpty());
    }

    @Test
    void exposesAllToolsWhenNoWhitelist() {
        KairoMcpServer server =
                KairoMcpServer.builder()
                        .toolExecutor(mockExecutor())
                        .tools(List.of(tool("echo", "Echo tool"), tool("fail", "Fail tool")))
                        .build();
        assertEquals(2, server.exposedTools().size());
        assertEquals("echo", server.exposedTools().get(0).name());
        assertEquals("fail", server.exposedTools().get(1).name());
    }

    @Test
    void whitelistFiltersTools() {
        KairoMcpServer server =
                KairoMcpServer.builder()
                        .toolExecutor(mockExecutor())
                        .tools(
                                List.of(
                                        tool("echo", "Echo"),
                                        tool("fail", "Fail"),
                                        tool("hidden", "Hidden")))
                        .allowedTools(Set.of("echo", "fail"))
                        .build();
        assertEquals(2, server.exposedTools().size());
        assertTrue(server.exposedTools().stream().anyMatch(t -> t.name().equals("echo")));
        assertTrue(server.exposedTools().stream().anyMatch(t -> t.name().equals("fail")));
        assertFalse(server.exposedTools().stream().anyMatch(t -> t.name().equals("hidden")));
    }

    @Test
    void emptyWhitelistExposesAll() {
        KairoMcpServer server =
                KairoMcpServer.builder()
                        .toolExecutor(mockExecutor())
                        .tools(List.of(tool("a", "A"), tool("b", "B")))
                        .allowedTools(Set.of())
                        .build();
        assertEquals(2, server.exposedTools().size());
    }

    @Test
    void exposedToolsIsUnmodifiable() {
        KairoMcpServer server =
                KairoMcpServer.builder()
                        .toolExecutor(mockExecutor())
                        .tools(List.of(tool("echo", "Echo")))
                        .build();
        assertThrows(
                UnsupportedOperationException.class,
                () -> server.exposedTools().add(tool("x", "X")));
    }

    @Test
    void toolWithSchemaExposed() {
        KairoMcpServer server =
                KairoMcpServer.builder()
                        .toolExecutor(mockExecutor())
                        .tools(List.of(toolWithSchema("schema-tool")))
                        .build();
        assertEquals(1, server.exposedTools().size());
        assertNotNull(server.exposedTools().get(0).inputSchema());
        assertTrue(server.exposedTools().get(0).inputSchema().properties().containsKey("input"));
    }

    @Test
    void stopWhenNotStartedIsNoop() {
        KairoMcpServer server = KairoMcpServer.builder().toolExecutor(mockExecutor()).build();
        server.stop().block();
        assertFalse(server.isRunning());
    }

    @Test
    void builderCustomizesNameAndVersion() {
        KairoMcpServer server =
                KairoMcpServer.builder()
                        .serverName("test-server")
                        .serverVersion("1.0.0")
                        .toolExecutor(mockExecutor())
                        .build();
        assertNotNull(server);
    }
}
