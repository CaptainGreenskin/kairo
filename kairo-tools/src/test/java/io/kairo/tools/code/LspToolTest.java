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
package io.kairo.tools.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.workspace.Workspace;
import io.kairo.core.lsp.LspClient;
import io.kairo.core.lsp.LspLocation;
import io.kairo.core.lsp.LspServerManager;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LspToolTest {

    @TempDir Path tempDir;

    private LspServerManager mockManager;
    private LspClient mockClient;
    private LspTool tool;
    private ToolContext ctx;

    @BeforeEach
    void setUp() {
        mockManager = mock(LspServerManager.class);
        mockClient = mock(LspClient.class);
        tool = new LspTool(mockManager);

        Workspace workspace = mock(Workspace.class);
        when(workspace.root()).thenReturn(tempDir);

        ctx = new ToolContext("agent", "session", null, workspace, null, null, Map.of());
    }

    @Test
    void definitionReturnsFormattedLocations() {
        when(mockManager.getClient(eq(".ts"), any())).thenReturn(mockClient);
        when(mockClient.gotoDefinition(any(), eq(9), eq(4)))
                .thenReturn(
                        List.of(new LspLocation("file:///workspace/src/main.ts", 20, 0, 20, 10)));

        ToolResult result =
                tool.execute(
                                Map.of(
                                        "operation",
                                        "definition",
                                        "filePath",
                                        "/workspace/src/app.ts",
                                        "line",
                                        10,
                                        "column",
                                        5),
                                ctx)
                        .block();

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Definition");
        assertThat(result.content()).contains("main.ts");
    }

    @Test
    void referencesReturnsMultipleLocations() {
        when(mockManager.getClient(eq(".ts"), any())).thenReturn(mockClient);
        when(mockClient.findReferences(any(), eq(4), eq(0)))
                .thenReturn(
                        List.of(
                                new LspLocation("file:///a.ts", 1, 0, 1, 5),
                                new LspLocation("file:///b.ts", 2, 0, 2, 5)));

        ToolResult result =
                tool.execute(
                                Map.of(
                                        "operation",
                                        "references",
                                        "filePath",
                                        "/workspace/src/app.ts",
                                        "line",
                                        5,
                                        "column",
                                        1),
                                ctx)
                        .block();

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("References");
        assertThat(result.content()).contains("2 result(s)");
    }

    @Test
    void hoverReturnsInfo() {
        when(mockManager.getClient(eq(".py"), any())).thenReturn(mockClient);
        when(mockClient.hover(any(), eq(0), eq(0))).thenReturn("def hello() -> None");

        ToolResult result =
                tool.execute(
                                Map.of(
                                        "operation",
                                        "hover",
                                        "filePath",
                                        "/workspace/test.py",
                                        "line",
                                        1,
                                        "column",
                                        1),
                                ctx)
                        .block();

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).isEqualTo("def hello() -> None");
    }

    @Test
    void hoverEmptyReturnsFallbackMessage() {
        when(mockManager.getClient(eq(".py"), any())).thenReturn(mockClient);
        when(mockClient.hover(any(), eq(0), eq(0))).thenReturn("");

        ToolResult result =
                tool.execute(
                                Map.of(
                                        "operation",
                                        "hover",
                                        "filePath",
                                        "/workspace/test.py",
                                        "line",
                                        1,
                                        "column",
                                        1),
                                ctx)
                        .block();

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("No hover information");
    }

    @Test
    void missingOperationReturnsError() {
        ToolResult result =
                tool.execute(Map.of("filePath", "/workspace/test.py", "line", 1), ctx).block();

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("operation is required");
    }

    @Test
    void missingFilePathReturnsError() {
        ToolResult result = tool.execute(Map.of("operation", "definition", "line", 1), ctx).block();

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("filePath is required");
    }

    @Test
    void unknownExtensionReturnsError() {
        ToolResult result =
                tool.execute(
                                Map.of(
                                        "operation", "definition",
                                        "filePath", "/workspace/file_no_ext",
                                        "line", 1),
                                ctx)
                        .block();

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("Cannot determine file extension");
    }

    @Test
    void noLanguageServerReturnsError() {
        when(mockManager.getClient(eq(".rs"), any())).thenReturn(null);

        ToolResult result =
                tool.execute(
                                Map.of(
                                        "operation", "definition",
                                        "filePath", "/workspace/main.rs",
                                        "line", 1),
                                ctx)
                        .block();

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("No language server available");
    }

    @Test
    void unknownOperationReturnsError() {
        when(mockManager.getClient(eq(".ts"), any())).thenReturn(mockClient);

        ToolResult result =
                tool.execute(
                                Map.of(
                                        "operation", "rename",
                                        "filePath", "/workspace/test.ts",
                                        "line", 1),
                                ctx)
                        .block();

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("Unknown operation");
    }

    @Test
    void definitionEmptyReturnsNoResults() {
        when(mockManager.getClient(eq(".ts"), any())).thenReturn(mockClient);
        when(mockClient.gotoDefinition(any(), eq(0), eq(0))).thenReturn(List.of());

        ToolResult result =
                tool.execute(
                                Map.of(
                                        "operation",
                                        "definition",
                                        "filePath",
                                        "/workspace/test.ts",
                                        "line",
                                        1,
                                        "column",
                                        1),
                                ctx)
                        .block();

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("No definition found");
    }
}
