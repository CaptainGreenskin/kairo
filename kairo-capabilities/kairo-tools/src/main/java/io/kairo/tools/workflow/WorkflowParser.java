/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.tools.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses {@code .kairo/workflows/*.yaml} files into {@link WorkflowDefinition}s.
 *
 * <p>Pure function (no I/O on the parse path) — file reading lives in {@link #fromFile(Path)} so
 * callers that already have the YAML text (tests, in-memory sources) can use {@link
 * #fromYaml(String)} directly.
 */
public final class WorkflowParser {

    private static final YAMLMapper YAML = new YAMLMapper();
    private static final ObjectMapper JSON_FOR_FALLBACK = new ObjectMapper();

    private WorkflowParser() {}

    /**
     * Read and parse a workflow file. Accepts both {@code .yaml}/{@code .yml} and {@code .json}
     * (Jackson YAML handles JSON-as-YAML, but we use the JSON mapper for cleaner error messages on
     * the {@code .json} suffix).
     */
    public static WorkflowDefinition fromFile(Path file) throws IOException {
        if (!Files.isReadable(file)) {
            throw new IOException("Workflow file not readable: " + file);
        }
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String name = file.getFileName().toString();
        return name.endsWith(".json") ? fromJson(content) : fromYaml(content);
    }

    /** Parse a YAML string. */
    public static WorkflowDefinition fromYaml(String text) throws IOException {
        JsonNode root = YAML.readTree(text);
        return fromTree(root);
    }

    /** Parse a JSON string. */
    public static WorkflowDefinition fromJson(String text) throws IOException {
        JsonNode root = JSON_FOR_FALLBACK.readTree(text);
        return fromTree(root);
    }

    private static WorkflowDefinition fromTree(JsonNode root) throws IOException {
        if (root == null || !root.isObject()) {
            throw new IOException("Workflow root must be a YAML/JSON object");
        }
        String name = textOrThrow(root, "name", "workflow 'name'");
        String description =
                root.path("description").isTextual() ? root.get("description").asText() : null;
        JsonNode stepsNode = root.get("steps");
        if (stepsNode == null || !stepsNode.isArray() || stepsNode.isEmpty()) {
            throw new IOException("Workflow 'steps' must be a non-empty array");
        }
        List<WorkflowDefinition.Step> steps = new ArrayList<>(stepsNode.size());
        int idx = 0;
        for (JsonNode stepNode : stepsNode) {
            idx++;
            steps.add(parseStep(stepNode, idx));
        }
        return new WorkflowDefinition(name, description, steps);
    }

    private static WorkflowDefinition.Step parseStep(JsonNode node, int idx) throws IOException {
        if (!node.isObject()) {
            throw new IOException("Workflow step #" + idx + " must be a YAML/JSON object");
        }
        String stepName = textOrThrow(node, "name", "step #" + idx + " 'name'");
        String tool = textOrThrow(node, "tool", "step '" + stepName + "' 'tool'");
        Map<String, Object> args = toArgsMap(node.get("args"));
        boolean continueOnError = node.path("continue_on_error").asBoolean(false);
        return new WorkflowDefinition.Step(stepName, tool, args, continueOnError);
    }

    private static String textOrThrow(JsonNode node, String field, String label)
            throws IOException {
        JsonNode v = node.get(field);
        if (v == null || !v.isTextual() || v.asText().isBlank()) {
            throw new IOException("Missing or blank required field: " + label);
        }
        return v.asText();
    }

    /**
     * Convert the {@code args} subtree into a {@code Map<String, Object>}. Nested objects / arrays
     * are converted recursively so the tool sees a plain Java map (the same shape it would get from
     * the model's JSON tool-call input).
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> toArgsMap(JsonNode argsNode) {
        if (argsNode == null || argsNode.isNull() || !argsNode.isObject()) {
            return Map.of();
        }
        try {
            return (Map<String, Object>) YAML.treeToValue(argsNode, Map.class);
        } catch (Exception e) {
            // Shouldn't happen for object nodes, but degrade gracefully rather than crash the
            // workflow load.
            return new LinkedHashMap<>();
        }
    }
}
