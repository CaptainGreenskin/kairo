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
package io.kairo.core.tool.permission;

import io.kairo.api.tool.ToolPermission;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A single permission rule using Claude Code-style {@code ToolName(glob)} syntax.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code "Read"} — matches all Read tool calls
 *   <li>{@code "Bash(npm test*)"} — matches Bash calls where primary arg glob-matches
 *   <li>{@code "mcp_*"} — matches all MCP tools
 *   <li>{@code "*"} — matches everything
 * </ul>
 *
 * @param toolPattern tool name glob pattern (lowercase)
 * @param argGlob argument value glob, or null to match any args
 * @param permission the permission to apply when this rule matches
 */
public record PermissionRule(String toolPattern, String argGlob, ToolPermission permission) {

    private static final List<String> PRIMARY_ARG_KEYS =
            List.of("command", "file_path", "filePath", "path", "url");

    /**
     * Parse a {@code ToolName(glob)} specification string.
     *
     * @param spec the rule spec, e.g. "Bash(npm test*)" or "Read"
     * @param permission the permission to assign
     * @return the parsed rule
     */
    public static PermissionRule parse(String spec, ToolPermission permission) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("Rule spec must not be empty");
        }

        String trimmed = spec.strip();
        int parenOpen = trimmed.indexOf('(');
        if (parenOpen >= 0) {
            if (!trimmed.endsWith(")")) {
                throw new IllegalArgumentException(
                        "Malformed rule spec (missing closing paren): " + spec);
            }
            String toolPart = trimmed.substring(0, parenOpen).strip().toLowerCase();
            String argPart = trimmed.substring(parenOpen + 1, trimmed.length() - 1).strip();
            if (toolPart.isEmpty()) {
                throw new IllegalArgumentException("Tool pattern must not be empty: " + spec);
            }
            return new PermissionRule(toolPart, argPart.isEmpty() ? null : argPart, permission);
        }

        return new PermissionRule(trimmed.toLowerCase(), null, permission);
    }

    /**
     * Check whether this rule matches the given tool invocation.
     *
     * @param toolName the tool name
     * @param args the tool arguments
     * @return true if both tool pattern and arg glob match
     */
    public boolean matches(String toolName, Map<String, Object> args) {
        if (!globMatches(toolPattern, toolName.toLowerCase())) {
            return false;
        }
        if (argGlob == null) {
            return true;
        }
        String primaryArg = extractPrimaryArg(args);
        if (primaryArg == null) {
            return false;
        }
        return globMatches(argGlob, primaryArg);
    }

    static String extractPrimaryArg(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return null;
        }
        for (String key : PRIMARY_ARG_KEYS) {
            Object val = args.get(key);
            if (val instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    static boolean globMatches(String glob, String value) {
        String regex = globToRegex(glob);
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(value).matches();
    }

    static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        sb.append(".*");
                        i += 2;
                        if (i < glob.length() && glob.charAt(i) == '/') {
                            i++;
                        }
                        continue;
                    }
                    sb.append(".*");
                }
                case '?' -> sb.append(".");
                case '.' -> sb.append("\\.");
                case '(' -> sb.append("\\(");
                case ')' -> sb.append("\\)");
                case '[' -> sb.append("\\[");
                case ']' -> sb.append("\\]");
                case '{' -> sb.append("\\{");
                case '}' -> sb.append("\\}");
                case '^' -> sb.append("\\^");
                case '$' -> sb.append("\\$");
                case '+' -> sb.append("\\+");
                case '|' -> sb.append("\\|");
                case '\\' -> sb.append("\\\\");
                default -> sb.append(c);
            }
            i++;
        }
        return sb.toString();
    }
}
