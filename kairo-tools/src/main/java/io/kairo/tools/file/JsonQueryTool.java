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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.kairo.api.tool.JsonSchema;
import io.kairo.api.tool.SyncTool;
import io.kairo.api.tool.Tool;
import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolContext;
import io.kairo.api.tool.ToolResult;
import io.kairo.api.tool.ToolSideEffect;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * Queries JSON data using a jq-like path expression (pure Java, no external tools).
 *
 * <p>Supported expressions: field access, array indexing, array expansion, pipe filtering, {@code
 * keys}, {@code length}, {@code type}.
 */
@Tool(
        name = "json_query",
        description =
                "Query JSON data using jq-like path expressions. Supports field access (.field),"
                        + " array indexing (.arr[0]), array expansion (.arr[]), pipe filtering"
                        + " (.arr[] | .field), keys, length, and type.",
        category = ToolCategory.FILE_AND_CODE,
        sideEffect = ToolSideEffect.READ_ONLY)
public class JsonQueryTool implements SyncTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public JsonSchema inputSchema() {
        java.util.Map<String, JsonSchema> props = new java.util.LinkedHashMap<>();
        props.put(
                "json",
                new JsonSchema(
                        "string",
                        null,
                        null,
                        "JSON document to query. Either inline JSON text or an absolute path starting with '/'."));
        props.put(
                "query",
                new JsonSchema(
                        "string",
                        null,
                        null,
                        "jq-style path expression, e.g. '.foo.bar', '.arr[0]', '.arr[] | .field'."));
        props.put(
                "pretty",
                new JsonSchema(
                        "boolean", null, null, "Pretty-print the result. Defaults to false."));
        return new JsonSchema("object", props, java.util.List.of("json", "query"), null);
    }

    @Override
    public Mono<ToolResult> execute(Map<String, Object> args, ToolContext ctx) {
        return Mono.fromCallable(() -> doExecute(args, ctx.workspace().root()));
    }

    private ToolResult doExecute(Map<String, Object> input, Path workspaceRoot) {
        String jsonInput = (String) input.get("json");
        String query = (String) input.get("query");
        boolean pretty = !Boolean.FALSE.equals(input.get("pretty"));

        if (jsonInput == null || jsonInput.isBlank()) {
            return error("Parameter 'json' is required");
        }
        if (query == null || query.isBlank()) {
            return error("Parameter 'query' is required");
        }

        // Resolve json: path or raw string
        JsonNode root;
        try {
            root = resolveJson(jsonInput, workspaceRoot);
        } catch (IOException e) {
            return error("Failed to read JSON: " + e.getMessage());
        }

        // Evaluate query
        List<JsonNode> results;
        try {
            results = evaluate(query.trim(), List.of(root));
        } catch (QueryException e) {
            return error("Query error: " + e.getMessage());
        }

        // Serialize output
        ObjectWriter writer = pretty ? MAPPER.writerWithDefaultPrettyPrinter() : MAPPER.writer();
        StringBuilder sb = new StringBuilder();
        try {
            for (JsonNode node : results) {
                if (node.isTextual()) {
                    sb.append(node.asText()).append("\n");
                } else {
                    sb.append(writer.writeValueAsString(node)).append("\n");
                }
            }
        } catch (JsonProcessingException e) {
            return error("Serialization error: " + e.getMessage());
        }

        String output = sb.toString().stripTrailing();
        return ToolResult.of(
                "json_query", output, false, Map.of("resultCount", results.size(), "query", query));
    }

    // ---- JSON resolution ----

    private JsonNode resolveJson(String jsonInput, Path workspaceRoot) throws IOException {
        String trimmed = jsonInput.trim();
        if (trimmed.startsWith("/") || trimmed.startsWith(".") || trimmed.startsWith("~")) {
            Path filePath =
                    trimmed.startsWith("~/")
                            ? Path.of(System.getProperty("user.home")).resolve(trimmed.substring(2))
                            : workspaceRoot.resolve(trimmed);
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            return MAPPER.readTree(content);
        }
        return MAPPER.readTree(trimmed);
    }

    // ---- Query evaluation ----

    private List<JsonNode> evaluate(String expr, List<JsonNode> inputs) throws QueryException {
        // Handle pipe: split on " | " (not inside brackets)
        int pipeIdx = findTopLevelPipe(expr);
        if (pipeIdx >= 0) {
            String left = expr.substring(0, pipeIdx).trim();
            String right = expr.substring(pipeIdx + 1).trim();
            List<JsonNode> intermediate = evaluate(left, inputs);
            return evaluate(right, intermediate);
        }

        // Evaluate expression on each input node
        List<JsonNode> results = new ArrayList<>();
        for (JsonNode node : inputs) {
            results.addAll(evalSingle(expr, node));
        }
        return results;
    }

    private List<JsonNode> evalSingle(String expr, JsonNode node) throws QueryException {
        // Terminal builtins
        if (expr.equals("keys")) return evalKeys(node);
        if (expr.equals("length")) return evalLength(node);
        if (expr.equals("type")) return List.of(MAPPER.valueToTree(typeName(node)));
        if (expr.equals(".")) return List.of(node);
        if (expr.isEmpty()) return List.of(node);

        // Path expression starting with "."
        if (expr.startsWith(".")) {
            return evalPath(expr.substring(1), node);
        }

        throw new QueryException("Unknown expression: '" + expr + "'");
    }

    private List<JsonNode> evalPath(String path, JsonNode node) throws QueryException {
        if (path.isEmpty()) return List.of(node);

        // Split first segment
        String segment;
        String rest;
        int dotIdx = findFirstDot(path);
        int bracketIdx = path.indexOf('[');

        if (bracketIdx >= 0 && (dotIdx < 0 || bracketIdx < dotIdx)) {
            // Array access comes first
            segment = path.substring(0, bracketIdx);
            rest = path.substring(bracketIdx);
        } else if (dotIdx >= 0) {
            segment = path.substring(0, dotIdx);
            rest = path.substring(dotIdx); // keep the leading dot
        } else {
            segment = path;
            rest = "";
        }

        // Resolve field if segment is non-empty
        JsonNode current = node;
        if (!segment.isEmpty()) {
            if (!current.isObject()) {
                throw new QueryException(
                        "Cannot access field '" + segment + "' on " + typeName(current));
            }
            current = current.path(segment);
            if (current.isMissingNode()) {
                return List.of(MAPPER.nullNode());
            }
        }

        // Handle array index / expansion
        if (rest.startsWith("[")) {
            int close = rest.indexOf(']');
            if (close < 0) throw new QueryException("Unclosed '[' in expression");
            String idx = rest.substring(1, close).trim();
            String afterBracket = rest.substring(close + 1);

            if (idx.isEmpty()) {
                // Array expansion: .arr[]
                if (!current.isArray()) {
                    throw new QueryException("Cannot iterate non-array: " + typeName(current));
                }
                List<JsonNode> expanded = new ArrayList<>();
                for (JsonNode elem : current) {
                    if (afterBracket.startsWith(".")) {
                        expanded.addAll(evalPath(afterBracket.substring(1), elem));
                    } else if (afterBracket.isEmpty()) {
                        expanded.add(elem);
                    } else {
                        expanded.addAll(evalPath(afterBracket, elem));
                    }
                }
                return expanded;
            } else {
                // Numeric index: .arr[0]
                try {
                    int index = Integer.parseInt(idx);
                    if (!current.isArray()) {
                        throw new QueryException("Cannot index non-array: " + typeName(current));
                    }
                    int size = current.size();
                    int resolved = index < 0 ? size + index : index;
                    if (resolved < 0 || resolved >= size) {
                        return List.of(MAPPER.nullNode());
                    }
                    JsonNode elem = current.get(resolved);
                    if (afterBracket.startsWith(".")) {
                        return evalPath(afterBracket.substring(1), elem);
                    }
                    return List.of(elem);
                } catch (NumberFormatException e) {
                    throw new QueryException("Invalid array index: '" + idx + "'");
                }
            }
        }

        // Continue with remaining path
        if (rest.startsWith(".")) {
            return evalPath(rest.substring(1), current);
        }
        return List.of(current);
    }

    private List<JsonNode> evalKeys(JsonNode node) throws QueryException {
        if (!node.isObject()) {
            throw new QueryException("keys requires an object, got " + typeName(node));
        }
        ArrayNode arr = MAPPER.createArrayNode();
        node.fieldNames().forEachRemaining(arr::add);
        return List.of(arr);
    }

    private List<JsonNode> evalLength(JsonNode node) {
        int len;
        if (node.isArray() || node.isObject()) {
            len = node.size();
        } else if (node.isTextual()) {
            len = node.asText().length();
        } else if (node.isNull()) {
            len = 0;
        } else {
            len = 1;
        }
        return List.of(MAPPER.valueToTree(len));
    }

    // ---- Helpers ----

    private String typeName(JsonNode node) {
        if (node.isObject()) return "object";
        if (node.isArray()) return "array";
        if (node.isTextual()) return "string";
        if (node.isNumber()) return "number";
        if (node.isBoolean()) return "boolean";
        if (node.isNull()) return "null";
        return "unknown";
    }

    /** Find first '.' that is not inside brackets. */
    private int findFirstDot(String path) {
        int depth = 0;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            else if (c == '.' && depth == 0) return i;
        }
        return -1;
    }

    /** Find top-level pipe '|' (not inside brackets or quotes). */
    private int findTopLevelPipe(String expr) {
        int depth = 0;
        for (int i = 0; i < expr.length() - 1; i++) {
            char c = expr.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            else if (c == '|' && depth == 0 && expr.charAt(i + 1) != '|') {
                return i;
            }
        }
        return -1;
    }

    private ToolResult error(String msg) {
        return ToolResult.error("json_query", msg);
    }

    static class QueryException extends Exception {
        QueryException(String message) {
            super(message);
        }
    }
}
