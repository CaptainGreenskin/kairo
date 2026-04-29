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
package io.kairo.core.tool;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ToolOutputCompressorTest {

    private static final int DEFAULT_MAX = 20_000;

    // ===== deduplicateLines =====

    @Test
    void deduplicateLines_threeIdentical_notCompressed() {
        String input = "line\nline\nline\n";
        String result = ToolOutputCompressor.deduplicateLines(input);
        assertEquals("line\nline\nline\n", result);
    }

    @Test
    void deduplicateLines_fiftyIdentical_compressedToMarker() {
        String line = "INFO: compiling...";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append(line).append("\n");
        }
        String result = ToolOutputCompressor.deduplicateLines(sb.toString());
        assertTrue(result.startsWith("INFO: compiling...\n"));
        assertTrue(result.contains("[repeated 49 more time(s)]"));
        assertFalse(
                result.contains(
                        "INFO: compiling...\nINFO: compiling...\nINFO: compiling...\nINFO: compiling"));
    }

    @Test
    void deduplicateLines_emptyString_returnsEmpty() {
        assertEquals("", ToolOutputCompressor.deduplicateLines(""));
    }

    @Test
    void deduplicateLines_null_returnsNull() {
        assertNull(ToolOutputCompressor.deduplicateLines(null));
    }

    @Test
    void deduplicateLines_noDuplicates_returnsOriginal() {
        String input = "alpha\nbeta\ngamma\n";
        assertEquals(input, ToolOutputCompressor.deduplicateLines(input));
    }

    @Test
    void deduplicateLines_fourIdentical_compressed() {
        // 4 identical lines: count=4, MAX_CONSECUTIVE_DUPS=3, 4 > 3 so it compresses
        String input = "dup\ndup\ndup\ndup\n";
        String result = ToolOutputCompressor.deduplicateLines(input);
        assertTrue(result.contains("[repeated 3 more time(s)]"));
    }

    @Test
    void deduplicateLines_fiveIdentical_compressed() {
        String input = "dup\ndup\ndup\ndup\ndup\n";
        String result = ToolOutputCompressor.deduplicateLines(input);
        assertTrue(result.contains("[repeated 4 more time(s)]"));
    }

    @Test
    void deduplicateLines_multipleGroups_eachGroupCompressed() {
        String a = "AAAA\n".repeat(10);
        String b = "BBBB\n".repeat(5);
        String input = a + b;
        String result = ToolOutputCompressor.deduplicateLines(input);
        assertTrue(result.contains("[repeated 9 more time(s)]"));
        assertTrue(result.contains("[repeated 4 more time(s)]"));
    }

    // ===== tailExtract =====

    @Test
    void tailExtract_contentBelowLimit_returnsOriginal() {
        String content = "short content";
        assertSame(content, ToolOutputCompressor.tailExtract(content, 100));
    }

    @Test
    void tailExtract_contentExceedsLimit_hasHeadTailMarker() {
        String content = "x".repeat(50_000);
        String result = ToolOutputCompressor.tailExtract(content, DEFAULT_MAX);
        assertTrue(result.startsWith("x".repeat(Math.min(2_000, DEFAULT_MAX / 3))));
        assertTrue(result.contains("chars omitted (middle)"));
        assertTrue(result.endsWith("x".repeat(Math.min(3_000, DEFAULT_MAX / 2))));
    }

    @Test
    void tailExtract_resultLength_withinBudgetPlusMarker() {
        String content = "x".repeat(50_000);
        String result = ToolOutputCompressor.tailExtract(content, DEFAULT_MAX);
        assertTrue(result.length() < DEFAULT_MAX + 100);
    }

    @Test
    void tailExtract_null_returnsNull() {
        assertNull(ToolOutputCompressor.tailExtract(null, 100));
    }

    @Test
    void tailExtract_smallContentLargeLimit_returnsOriginal() {
        String content = "hello";
        assertSame(content, ToolOutputCompressor.tailExtract(content, 1_000_000));
    }

    // ===== compress (integration) =====

    @Test
    void compress_withLargeDuplicates_compressesUnderLimit() {
        StringBuilder sb = new StringBuilder();
        sb.append("header info\n");
        for (int i = 0; i < 10_000; i++) {
            sb.append("INFO: processing item\n");
        }
        sb.append("FAILURE: 3 tests failed, 2 errors\n");
        String content = sb.toString();
        String result = ToolOutputCompressor.compress(content, DEFAULT_MAX);
        assertTrue(result.length() <= DEFAULT_MAX, "Compressed output should be within limit");
        assertTrue(result.contains("[repeated"));
    }

    @Test
    void compress_preservesTailContent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10_000; i++) {
            sb.append("compiling module ").append(i).append("\n");
        }
        String tailMarker = "FAILURE: test suite failed";
        sb.append(tailMarker);
        String content = sb.toString();
        String result = ToolOutputCompressor.compress(content, DEFAULT_MAX);
        assertTrue(result.contains(tailMarker), "Tail content (error summary) must be preserved");
    }

    @Test
    void compress_contentUnderLimit_returnsDedupedOnly() {
        String input = "a\na\na\na\nb\n";
        String result = ToolOutputCompressor.compress(input, 10_000);
        // Under limit: should not tail-extract (no "chars omitted" marker)
        assertFalse(result.contains("chars omitted"));
        assertTrue(result.contains("[repeated"));
    }

    @Test
    void compress_null_returnsNull() {
        assertNull(ToolOutputCompressor.compress(null, 100));
    }

    @Test
    void compress_emptyString_returnsEmpty() {
        assertEquals("", ToolOutputCompressor.compress("", 100));
    }
}
