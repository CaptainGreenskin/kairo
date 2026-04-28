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
package io.kairo.tools.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolHandler;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import io.kairo.api.workspace.Workspace;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders Mustache templates (pure Java subset — no external library).
 *
 * <p>Supported syntax: {@code {{var}}} (HTML-escaped), {@code {{{var}}}} (raw), {@code
 * {{#sec}}...{{/sec}}} (truthy/array section), {@code {{^sec}}...{{/sec}}} (inverted section),
 * {@code {{! comment }}}.
 */
@Tool(
        name = "template_render",
        description =
                "Render a Mustache template with provided variables. Supports {{var}} (escaped),"
                        + " {{{var}}} (raw), {{#section}}...{{/section}} (loop/conditional),"
                        + " {{^section}}...{{/section}} (inverted), and {{! comment }}."
                        + " template may be a string or a file path starting with /.",
        category = ToolCategory.FILE_AND_CODE,
        sideEffect = ToolSideEffect.WRITE)
public class TemplateRenderTool implements ToolHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ToolResult execute(Map<String, Object> input) {
        return doExecute(input, Workspace.cwd().root());
    }

    @Override
    public ToolResult execute(Map<String, Object> input, ToolContext context) {
        return doExecute(input, context.workspace().root());
    }

    private ToolResult doExecute(Map<String, Object> input, Path workspaceRoot) {
        String templateInput = (String) input.get("template");
        String variablesJson = (String) input.get("variables");
        String outputPath = (String) input.get("outputPath");

        if (templateInput == null || templateInput.isBlank()) {
            return error("Parameter 'template' is required");
        }
        if (variablesJson == null || variablesJson.isBlank()) {
            return error("Parameter 'variables' is required");
        }

        // Resolve template content
        String template;
        try {
            template = resolveTemplate(templateInput, workspaceRoot);
        } catch (IOException e) {
            return error("Failed to read template file: " + e.getMessage());
        }

        // Parse variables
        JsonNode vars;
        try {
            vars = MAPPER.readTree(variablesJson);
            if (!vars.isObject()) {
                return error("'variables' must be a JSON object");
            }
        } catch (Exception e) {
            return error("Failed to parse variables JSON: " + e.getMessage());
        }

        // Render
        String rendered;
        try {
            rendered = render(template, vars);
        } catch (RenderException e) {
            return error("Template render error: " + e.getMessage());
        }

        // Write output if requested
        if (outputPath != null && !outputPath.isBlank()) {
            try {
                Path target =
                        outputPath.startsWith("/")
                                ? Path.of(outputPath)
                                : workspaceRoot.resolve(outputPath);
                Files.createDirectories(target.getParent());
                Files.writeString(target, rendered, StandardCharsets.UTF_8);
            } catch (IOException e) {
                return error("Failed to write output file: " + e.getMessage());
            }
        }

        int linesRendered = rendered.isEmpty() ? 0 : rendered.split("\n", -1).length;
        return new ToolResult(
                "template_render",
                rendered,
                false,
                Map.of(
                        "linesRendered",
                        linesRendered,
                        "outputPath",
                        outputPath != null ? outputPath : ""));
    }

    // ---- Template resolution ----

    private String resolveTemplate(String templateInput, Path workspaceRoot) throws IOException {
        String trimmed = templateInput.trim();
        if (trimmed.startsWith("/") || trimmed.startsWith("~/")) {
            Path filePath =
                    trimmed.startsWith("~/")
                            ? Path.of(System.getProperty("user.home")).resolve(trimmed.substring(2))
                            : Path.of(trimmed);
            return Files.readString(filePath, StandardCharsets.UTF_8);
        }
        if (trimmed.startsWith("./") || trimmed.startsWith("../")) {
            return Files.readString(workspaceRoot.resolve(trimmed), StandardCharsets.UTF_8);
        }
        return templateInput;
    }

    // ---- Mustache renderer ----

    /**
     * Tokenize and render the template against the given JSON context stack.
     *
     * <p>The context stack supports nested section lookups: innermost context is checked first.
     */
    String render(String template, JsonNode rootContext) throws RenderException {
        List<JsonNode> contextStack = new ArrayList<>();
        contextStack.add(rootContext);
        return renderSegment(template, 0, template.length(), contextStack).text;
    }

    private record RenderResult(String text, int endPos) {}

    private RenderResult renderSegment(String src, int start, int limit, List<JsonNode> stack)
            throws RenderException {
        StringBuilder sb = new StringBuilder();
        int i = start;

        while (i < limit) {
            int open = src.indexOf("{{", i);
            if (open < 0 || open >= limit) {
                sb.append(src, i, limit);
                break;
            }
            sb.append(src, i, open);

            // Determine tag type
            if (open + 2 < limit && src.charAt(open + 2) == '{') {
                // Triple mustache {{{var}}}
                int close = src.indexOf("}}}", open + 3);
                if (close < 0) throw new RenderException("Unclosed '{{{' tag");
                String key = src.substring(open + 3, close).trim();
                sb.append(resolveRaw(key, stack));
                i = close + 3;
            } else {
                int close = src.indexOf("}}", open + 2);
                if (close < 0) throw new RenderException("Unclosed '{{' tag");
                String inner = src.substring(open + 2, close).trim();
                i = close + 2;

                if (inner.isEmpty()) {
                    // Empty tag — skip
                } else if (inner.charAt(0) == '!') {
                    // Comment — skip
                } else if (inner.charAt(0) == '#') {
                    // Section open
                    String key = inner.substring(1).trim();
                    String closeTag = "{{/" + key + "}}";
                    int sectionEnd = findSectionEnd(src, i, key, limit);
                    if (sectionEnd < 0)
                        throw new RenderException("Unclosed section '{{#" + key + "}}'");
                    String sectionBody = src.substring(i, sectionEnd);
                    sb.append(renderSection(key, sectionBody, stack, false));
                    i = sectionEnd + closeTag.length();
                } else if (inner.charAt(0) == '^') {
                    // Inverted section
                    String key = inner.substring(1).trim();
                    String closeTag = "{{/" + key + "}}";
                    int sectionEnd = findSectionEnd(src, i, key, limit);
                    if (sectionEnd < 0)
                        throw new RenderException("Unclosed section '{{^" + key + "}}'");
                    String sectionBody = src.substring(i, sectionEnd);
                    sb.append(renderSection(key, sectionBody, stack, true));
                    i = sectionEnd + closeTag.length();
                } else if (inner.charAt(0) == '/') {
                    // Unexpected close tag — handled by parent call
                    i = open;
                    break;
                } else {
                    // Normal escaped variable
                    sb.append(htmlEscape(resolveRaw(inner, stack)));
                }
            }
        }

        return new RenderResult(sb.toString(), i);
    }

    /**
     * Find the position of the matching close tag, handling nested same-name sections.
     *
     * @return start index of the {@code {{/key}}} tag, or -1 if not found
     */
    private int findSectionEnd(String src, int from, String key, int limit) {
        String openTag = "{{#" + key + "}}";
        String closeTag = "{{/" + key + "}}";
        int depth = 1;
        int i = from;
        while (i < limit) {
            int nextOpen = src.indexOf(openTag, i);
            int nextClose = src.indexOf(closeTag, i);
            if (nextClose < 0 || nextClose >= limit) return -1;
            if (nextOpen >= 0 && nextOpen < nextClose) {
                depth++;
                i = nextOpen + openTag.length();
            } else {
                depth--;
                if (depth == 0) return nextClose;
                i = nextClose + closeTag.length();
            }
        }
        return -1;
    }

    private String renderSection(String key, String body, List<JsonNode> stack, boolean inverted)
            throws RenderException {
        JsonNode value = resolve(key, stack);
        boolean truthy = isTruthy(value);

        if (inverted) {
            return truthy ? "" : renderSegment(body, 0, body.length(), stack).text;
        }

        if (!truthy) return "";

        if (value != null && value.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode elem : value) {
                List<JsonNode> childStack = new ArrayList<>(stack);
                childStack.add(0, elem);
                sb.append(renderSegment(body, 0, body.length(), childStack).text);
            }
            return sb.toString();
        }

        if (value != null && value.isObject()) {
            List<JsonNode> childStack = new ArrayList<>(stack);
            childStack.add(0, value);
            return renderSegment(body, 0, body.length(), childStack).text;
        }

        return renderSegment(body, 0, body.length(), stack).text;
    }

    // ---- Context resolution ----

    private JsonNode resolve(String key, List<JsonNode> stack) {
        if (key.equals(".")) {
            return stack.isEmpty() ? null : stack.get(0);
        }
        for (JsonNode ctx : stack) {
            if (ctx != null && ctx.isObject()) {
                JsonNode val = ctx.get(key);
                if (val != null) return val;
            }
        }
        return null;
    }

    private String resolveRaw(String key, List<JsonNode> stack) {
        JsonNode node = resolve(key, stack);
        if (node == null || node.isNull()) return "";
        if (node.isTextual()) return node.asText();
        if (node.isNumber()) return node.asText();
        if (node.isBoolean()) return node.asText();
        return node.toString();
    }

    private boolean isTruthy(JsonNode node) {
        if (node == null || node.isNull()) return false;
        if (node.isBoolean()) return node.asBoolean();
        if (node.isArray()) return node.size() > 0;
        if (node.isTextual()) return !node.asText().isEmpty();
        if (node.isNumber()) return node.asDouble() != 0;
        if (node.isObject()) return true;
        return false;
    }

    // ---- HTML escaping ----

    private String htmlEscape(String s) {
        if (s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#x27;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private ToolResult error(String msg) {
        return new ToolResult("template_render", msg, true, Map.of());
    }

    static class RenderException extends Exception {
        RenderException(String message) {
            super(message);
        }
    }
}
