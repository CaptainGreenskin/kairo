/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.plugin.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.kairo.api.agent.SubagentDefinition;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses an {@code agents/*.md} file (Claude Code subagent format) into a {@link
 * SubagentDefinition}. The file shape is YAML frontmatter followed by markdown body that becomes
 * the subagent's system prompt.
 *
 * <p>Recognised frontmatter fields (all optional):
 *
 * <ul>
 *   <li>{@code name} — defaults to filename minus {@code .md}
 *   <li>{@code description} — defaults to empty string
 *   <li>{@code model} — model alias / provider name; null means inherit parent
 *   <li>{@code tools} — list of tool names the subagent may use; empty means inherit parent
 *   <li>{@code color} — cosmetic; ignored
 * </ul>
 *
 * <p>Files without frontmatter are accepted: name comes from the filename and the entire body is
 * the system prompt.
 */
public final class SubagentMarkdownParser {

    private static final YAMLMapper yaml = new YAMLMapper();

    /**
     * Parses {@code file} into a {@link SubagentDefinition}. The {@code namespace} is stored on the
     * definition (used by {@code SubagentDefinition.qualifiedName()}).
     */
    public SubagentDefinition parse(Path file, String namespace) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        String defaultName = stripExtension(file.getFileName().toString());

        Frontmatter fm = splitFrontmatter(content);
        String name = defaultName;
        String description = "";
        String model = null;
        List<String> tools = List.of();

        if (fm.frontmatterYaml != null) {
            JsonNode fmNode = yaml.readTree(fm.frontmatterYaml);
            if (fmNode != null && fmNode.isObject()) {
                if (fmNode.hasNonNull("name")) name = fmNode.get("name").asText(defaultName);
                if (fmNode.hasNonNull("description"))
                    description = fmNode.get("description").asText("");
                if (fmNode.hasNonNull("model")) model = fmNode.get("model").asText(null);
                if (fmNode.hasNonNull("tools")) tools = readStringList(fmNode.get("tools"));
            }
        }

        String systemPrompt = fm.body;
        return new SubagentDefinition(name, description, systemPrompt, tools, model, namespace);
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null) return List.of();
        if (node.isArray()) {
            List<String> out = new ArrayList<>();
            for (JsonNode el : node) {
                if (el.isTextual()) out.add(el.asText());
            }
            return out;
        }
        if (node.isTextual()) {
            // YAML often inlines comma-separated lists as strings.
            String[] parts = node.asText().split(",");
            List<String> out = new ArrayList<>();
            for (String p : parts) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty()) out.add(trimmed);
            }
            return out;
        }
        return List.of();
    }

    /**
     * Splits a markdown file with optional YAML frontmatter into (yaml, body). The frontmatter
     * delimiter is {@code ---} on a line by itself; absence of frontmatter is fine.
     */
    static Frontmatter splitFrontmatter(String content) {
        if (content == null) return new Frontmatter(null, "");
        if (!content.startsWith("---")) {
            return new Frontmatter(null, content);
        }
        // Find the closing --- line.
        int firstNl = content.indexOf('\n');
        if (firstNl < 0) return new Frontmatter(null, content);
        // Search after the opening marker.
        int closeIdx = content.indexOf("\n---", firstNl);
        if (closeIdx < 0) return new Frontmatter(null, content);
        String yaml = content.substring(firstNl + 1, closeIdx);
        // Body starts after the closing --- line + its newline.
        int bodyStart = content.indexOf('\n', closeIdx + 1);
        String body = bodyStart < 0 ? "" : content.substring(bodyStart + 1).stripLeading();
        return new Frontmatter(yaml, body);
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    record Frontmatter(String frontmatterYaml, String body) {}
}
