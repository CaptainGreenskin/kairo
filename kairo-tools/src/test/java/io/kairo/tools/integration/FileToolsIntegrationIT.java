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
package io.kairo.tools.integration;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tool.ToolResult;
import io.kairo.tools.file.EditTool;
import io.kairo.tools.file.GlobTool;
import io.kairo.tools.file.GrepTool;
import io.kairo.tools.file.ReadTool;
import io.kairo.tools.file.WriteTool;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for file tools (ReadTool, WriteTool, GrepTool, GlobTool, EditTool) that
 * exercise real filesystem operations. All file I/O is confined to a JUnit {@code @TempDir}.
 */
@Tag("integration")
class FileToolsIntegrationIT {

    private ReadTool readTool;
    private WriteTool writeTool;
    private GrepTool grepTool;
    private GlobTool globTool;
    private EditTool editTool;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        readTool = new ReadTool();
        writeTool = new WriteTool();
        grepTool = new GrepTool();
        globTool = new GlobTool();
        editTool = new EditTool();
    }

    // ── ReadTool integration tests ─────────────────────────────────────

    @Test
    void readFile_existingFile_returnsContent() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "Hello, Kairo!\nSecond line.\n");

        ToolResult result = readTool.execute(Map.of("path", file.toString()));

        assertFalse(result.isError());
        assertTrue(result.content().contains("Hello, Kairo!"));
        assertTrue(result.content().contains("Second line."));
        assertEquals(file.toString(), result.metadata().get("path"));
    }

    @Test
    void readFile_nonExistent_returnsError() {
        String missing = tempDir.resolve("does_not_exist.txt").toString();

        ToolResult result = readTool.execute(Map.of("path", missing));

        assertTrue(result.isError());
        assertTrue(result.content().contains("File not found"));
    }

    @Test
    void readFile_largeFile_handlesGracefully() throws IOException {
        // Generate a file with 3000 lines (exceeds MAX_LINES_WITHOUT_RANGE = 2000)
        Path file = tempDir.resolve("large.txt");
        String content =
                IntStream.rangeClosed(1, 3000)
                        .mapToObj(i -> "Line number " + i)
                        .collect(Collectors.joining("\n"));
        Files.writeString(file, content);

        ToolResult result = readTool.execute(Map.of("path", file.toString()));

        assertFalse(result.isError());
        // Should warn about large file and show only first 2000 lines
        assertTrue(result.content().contains("3000 lines"));
        assertTrue(result.content().contains("Line number 1"));
        assertEquals(3000, result.metadata().get("totalLines"));
    }

    @Test
    void readFile_binaryContent_handledCorrectly() throws IOException {
        // Write valid UTF-8 content that includes special characters
        Path file = tempDir.resolve("special.txt");
        byte[] bytes = "Hello\u0000World\tTab\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file, bytes);

        // ReadTool uses Files.readAllLines which may fail on binary-like content
        ToolResult result = readTool.execute(Map.of("path", file.toString()));
        // Either succeeds with content or fails gracefully — should not throw
        assertNotNull(result);
    }

    // ── WriteTool integration tests ────────────────────────────────────

    @Test
    void writeFile_newFile_createsSuccessfully() {
        String filePath = tempDir.resolve("new_file.txt").toString();

        ToolResult result =
                writeTool.execute(Map.of("path", filePath, "content", "brand new content"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("Successfully wrote"));
        assertTrue(Files.exists(Path.of(filePath)));
    }

    @Test
    void writeFile_overwrite_updatesContent() throws IOException {
        Path file = tempDir.resolve("overwrite.txt");
        Files.writeString(file, "original content");

        ToolResult writeResult =
                writeTool.execute(Map.of("path", file.toString(), "content", "updated content"));
        assertFalse(writeResult.isError());

        // Verify via ReadTool
        ToolResult readResult = readTool.execute(Map.of("path", file.toString()));
        assertFalse(readResult.isError());
        assertTrue(readResult.content().contains("updated content"));
        assertFalse(readResult.content().contains("original content"));
    }

    @Test
    void writeFile_withSubdirectories_createsParentDirs() {
        String filePath =
                tempDir.resolve("a/b/c/deep_file.txt").toString();

        ToolResult result =
                writeTool.execute(Map.of("path", filePath, "content", "nested content"));

        assertFalse(result.isError());
        assertTrue(Files.exists(Path.of(filePath)));
    }

    // ── GrepTool integration tests ─────────────────────────────────────

    @Test
    void grepTool_matchingPattern_findsResults() throws IOException {
        // Set up a small directory with searchable files
        Path file1 = tempDir.resolve("app.java");
        Files.writeString(file1, "public class App {\n    // TODO: implement\n}\n");
        Path file2 = tempDir.resolve("main.java");
        Files.writeString(file2, "public static void main(String[] args) {\n}\n");

        ToolResult result =
                grepTool.execute(Map.of("pattern", "TODO", "path", tempDir.toString()));

        assertFalse(result.isError());
        assertTrue(result.content().contains("TODO"));
        assertTrue(result.content().contains("app.java"));
        assertEquals(1, result.metadata().get("count"));
    }

    @Test
    void grepTool_noMatch_returnsEmpty() throws IOException {
        Path file = tempDir.resolve("clean.txt");
        Files.writeString(file, "No matching content here.\n");

        ToolResult result =
                grepTool.execute(
                        Map.of("pattern", "NONEXISTENT_STRING_xyz", "path", tempDir.toString()));

        assertFalse(result.isError());
        assertTrue(result.content().contains("No matches found"));
        assertEquals(0, result.metadata().get("count"));
    }

    @Test
    void grepTool_withGlobFilter_restrictsSearch() throws IOException {
        Path javaFile = tempDir.resolve("code.java");
        Files.writeString(javaFile, "public class Foo {}\n");
        Path txtFile = tempDir.resolve("notes.txt");
        Files.writeString(txtFile, "public notes about something\n");

        ToolResult result =
                grepTool.execute(
                        Map.of(
                                "pattern", "public",
                                "path", tempDir.toString(),
                                "glob", "*.java"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("code.java"));
        assertFalse(result.content().contains("notes.txt"));
    }

    @Test
    void grepTool_invalidRegex_returnsError() {
        ToolResult result =
                grepTool.execute(Map.of("pattern", "[invalid(", "path", tempDir.toString()));

        assertTrue(result.isError());
        assertTrue(result.content().contains("Invalid regex"));
    }

    // ── GlobTool integration tests ─────────────────────────────────────

    @Test
    void globTool_existingDir_returnsEntries() throws IOException {
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("one.java"), "class One {}");
        Files.writeString(srcDir.resolve("two.java"), "class Two {}");
        Files.writeString(tempDir.resolve("readme.md"), "# Readme");

        ToolResult result =
                globTool.execute(
                        Map.of("pattern", "**/*.java", "path", tempDir.toString()));

        assertFalse(result.isError());
        assertTrue(result.content().contains("one.java"));
        assertTrue(result.content().contains("two.java"));
        assertFalse(result.content().contains("readme.md"));
        assertEquals(2, result.metadata().get("count"));
    }

    @Test
    void globTool_nonExistentDir_returnsError() {
        String missing = tempDir.resolve("nonexistent_dir").toString();

        ToolResult result =
                globTool.execute(Map.of("pattern", "**/*", "path", missing));

        assertTrue(result.isError());
        assertTrue(result.content().contains("Not a directory"));
    }

    @Test
    void globTool_noMatches_returnsEmptyResult() throws IOException {
        Files.writeString(tempDir.resolve("only.txt"), "data");

        ToolResult result =
                globTool.execute(
                        Map.of("pattern", "**/*.xml", "path", tempDir.toString()));

        assertFalse(result.isError());
        assertTrue(result.content().contains("No files matched"));
        assertEquals(0, result.metadata().get("count"));
    }

    // ── EditTool integration tests ─────────────────────────────────────

    @Test
    void editTool_preciseReplacement_updatesFile() throws IOException {
        Path file = tempDir.resolve("editable.txt");
        Files.writeString(file, "Hello World\nFoo Bar\n");

        ToolResult result =
                editTool.execute(
                        Map.of(
                                "path", file.toString(),
                                "originalText", "Foo Bar",
                                "newText", "Baz Qux"));

        assertFalse(result.isError());
        assertTrue(result.content().contains("Successfully edited"));

        // Verify the file was updated
        String updated = Files.readString(file);
        assertTrue(updated.contains("Baz Qux"));
        assertFalse(updated.contains("Foo Bar"));
    }

    @Test
    void editTool_textNotFound_returnsError() throws IOException {
        Path file = tempDir.resolve("stable.txt");
        Files.writeString(file, "unchanged content\n");

        ToolResult result =
                editTool.execute(
                        Map.of(
                                "path", file.toString(),
                                "originalText", "nonexistent text",
                                "newText", "replacement"));

        assertTrue(result.isError());
        assertTrue(result.content().contains("Could not find"));
    }

    @Test
    void editTool_ambiguousMatch_returnsError() throws IOException {
        Path file = tempDir.resolve("ambiguous.txt");
        Files.writeString(file, "apple\norange\napple\n");

        ToolResult result =
                editTool.execute(
                        Map.of(
                                "path", file.toString(),
                                "originalText", "apple",
                                "newText", "banana"));

        assertTrue(result.isError());
        assertTrue(result.content().contains("occurrences"));
    }

    // ── Cross-tool integration tests ───────────────────────────────────

    @Test
    void writeAndRead_roundTrip_preservesContent() {
        String filePath = tempDir.resolve("roundtrip.txt").toString();
        String original = "Line 1\nLine 2\nLine 3\n";

        // Write
        ToolResult writeResult =
                writeTool.execute(Map.of("path", filePath, "content", original));
        assertFalse(writeResult.isError());

        // Read
        ToolResult readResult = readTool.execute(Map.of("path", filePath));
        assertFalse(readResult.isError());
        assertTrue(readResult.content().contains("Line 1"));
        assertTrue(readResult.content().contains("Line 2"));
        assertTrue(readResult.content().contains("Line 3"));
    }

    @Test
    void writeGrepGlob_crossToolWorkflow() {
        // Write several files
        writeTool.execute(
                Map.of(
                        "path", tempDir.resolve("src/Main.java").toString(),
                        "content", "public class Main { // entry point }"));
        writeTool.execute(
                Map.of(
                        "path", tempDir.resolve("src/Helper.java").toString(),
                        "content", "public class Helper { // utility }"));
        writeTool.execute(
                Map.of(
                        "path", tempDir.resolve("docs/readme.md").toString(),
                        "content", "# Documentation"));

        // Glob should find Java files
        ToolResult globResult =
                globTool.execute(
                        Map.of(
                                "pattern", "**/*.java",
                                "path", tempDir.toString()));
        assertFalse(globResult.isError());
        assertEquals(2, globResult.metadata().get("count"));

        // Grep should find the entry point comment
        ToolResult grepResult =
                grepTool.execute(
                        Map.of(
                                "pattern", "entry point",
                                "path", tempDir.toString()));
        assertFalse(grepResult.isError());
        assertTrue(grepResult.content().contains("Main.java"));
    }

    @Test
    void fileTools_concurrentAccess_threadSafe() throws Exception {
        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<ToolResult>> futures = new ArrayList<>();

        // Write files concurrently
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            futures.add(
                    executor.submit(
                            () -> {
                                latch.await();
                                return writeTool.execute(
                                        Map.of(
                                                "path",
                                                tempDir.resolve("concurrent_" + idx + ".txt")
                                                        .toString(),
                                                "content",
                                                "Content from thread " + idx));
                            }));
        }

        latch.countDown(); // Release all threads simultaneously
        for (Future<ToolResult> future : futures) {
            ToolResult result = future.get();
            assertFalse(result.isError(), "Concurrent write failed: " + result.content());
        }

        // Read all files back concurrently
        List<Future<ToolResult>> readFutures = new ArrayList<>();
        CountDownLatch readLatch = new CountDownLatch(1);
        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            readFutures.add(
                    executor.submit(
                            () -> {
                                readLatch.await();
                                return readTool.execute(
                                        Map.of(
                                                "path",
                                                tempDir.resolve("concurrent_" + idx + ".txt")
                                                        .toString()));
                            }));
        }

        readLatch.countDown();
        for (int i = 0; i < threadCount; i++) {
            ToolResult result = readFutures.get(i).get();
            assertFalse(result.isError(), "Concurrent read failed: " + result.content());
            assertTrue(result.content().contains("Content from thread " + i));
        }

        executor.shutdown();
    }
}
