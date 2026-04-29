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

/**
 * Strategies for compressing oversized tool output before context insertion. Applied after result
 * budget check; all strategies preserve output under the limit unchanged.
 */
public final class ToolOutputCompressor {

    private static final int HEAD_CHARS = 2_000;
    private static final int TAIL_CHARS = 3_000;
    private static final int MAX_CONSECUTIVE_DUPS = 3;

    private ToolOutputCompressor() {}

    /**
     * Compress content that exceeds maxChars using tail-extraction strategy: keep first HEAD_CHARS
     * + last TAIL_CHARS, omit middle with a marker.
     */
    public static String tailExtract(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) return content;
        int head = Math.min(HEAD_CHARS, maxChars / 3);
        int tail = Math.min(TAIL_CHARS, maxChars / 2);
        if (head + tail >= content.length()) return content;
        int omitted = content.length() - head - tail;
        return content.substring(0, head)
                + "\n[... "
                + omitted
                + " chars omitted (middle) ...]\n"
                + content.substring(content.length() - tail);
    }

    /**
     * Deduplicate consecutive identical lines, replacing repeats with a count marker. Example: 50
     * identical "INFO: compiling..." lines → "INFO: compiling...\n[repeated 49 more time(s)]"
     */
    public static String deduplicateLines(String content) {
        if (content == null || content.isEmpty()) return content;
        boolean hasTrailingNewline = content.endsWith("\n");
        String toProcess =
                hasTrailingNewline ? content.substring(0, content.length() - 1) : content;
        String[] lines = toProcess.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        String groupLine = lines[0];
        int groupCount = 1;

        for (int i = 1; i < lines.length; i++) {
            if (lines[i].equals(groupLine)) {
                groupCount++;
            } else {
                flushGroup(sb, groupLine, groupCount);
                groupLine = lines[i];
                groupCount = 1;
            }
        }
        flushGroup(sb, groupLine, groupCount);
        if (hasTrailingNewline) {
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void flushGroup(StringBuilder sb, String groupLine, int groupCount) {
        if (groupCount > MAX_CONSECUTIVE_DUPS) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(groupLine)
                    .append("\n[repeated ")
                    .append(groupCount - 1)
                    .append(" more time(s)]");
        } else {
            if (sb.length() > 0) sb.append('\n');
            sb.append(groupLine);
            for (int j = 1; j < groupCount; j++) {
                sb.append('\n').append(groupLine);
            }
        }
    }

    /**
     * Apply compression pipeline: deduplicate lines first, then tail-extract if still over limit.
     */
    public static String compress(String content, int maxChars) {
        if (content == null) return null;
        String deduped = deduplicateLines(content);
        if (deduped.length() <= maxChars) return deduped;
        return tailExtract(deduped, maxChars);
    }
}
