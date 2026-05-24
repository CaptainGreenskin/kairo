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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.agent.SubagentDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubagentMarkdownParserTest {

    private final SubagentMarkdownParser parser = new SubagentMarkdownParser();

    @Test
    void parsesClaudeCodeStyleAgentFile(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("code-reviewer.md");
        Files.writeString(
                file,
                """
                ---
                name: code-reviewer
                description: Reviews code for adherence to guidelines.
                model: opus
                color: green
                tools: ["read", "grep"]
                ---

                You are an expert code reviewer.
                """);
        SubagentDefinition def = parser.parse(file, "my-plugin");
        assertThat(def.name()).isEqualTo("code-reviewer");
        assertThat(def.description()).isEqualTo("Reviews code for adherence to guidelines.");
        assertThat(def.model()).isEqualTo("opus");
        assertThat(def.tools()).containsExactly("read", "grep");
        assertThat(def.namespace()).isEqualTo("my-plugin");
        assertThat(def.systemPrompt()).startsWith("You are an expert code reviewer.");
    }

    @Test
    void colorFieldIsAcceptedAndIgnored(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("c.md");
        Files.writeString(file, "---\nname: c\ncolor: green\n---\n\nbody");
        SubagentDefinition def = parser.parse(file, null);
        assertThat(def.name()).isEqualTo("c");
        assertThat(def.systemPrompt()).isEqualTo("body");
    }

    @Test
    void fileWithoutFrontmatterUsesFilenameAndFullBody(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("plain.md");
        Files.writeString(file, "Just the prompt body, no frontmatter.\n");
        SubagentDefinition def = parser.parse(file, null);
        assertThat(def.name()).isEqualTo("plain");
        assertThat(def.systemPrompt()).isEqualTo("Just the prompt body, no frontmatter.\n");
        assertThat(def.tools()).isEmpty();
        assertThat(def.model()).isNull();
    }

    @Test
    void filenameWithoutMdExtensionStillParses(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("no-ext");
        Files.writeString(file, "---\ndescription: hi\n---\n\nbody");
        SubagentDefinition def = parser.parse(file, null);
        assertThat(def.name()).isEqualTo("no-ext");
    }

    @Test
    void multilineDescriptionIsPreserved(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("multi.md");
        Files.writeString(
                file,
                """
                ---
                name: multi
                description: |
                  Line 1
                  Line 2
                ---

                body
                """);
        SubagentDefinition def = parser.parse(file, null);
        assertThat(def.description()).contains("Line 1").contains("Line 2");
    }

    @Test
    void toolsAsCommaSeparatedStringIsAccepted(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("t.md");
        Files.writeString(file, "---\nname: t\ntools: read,grep,bash\n---\n\nbody");
        SubagentDefinition def = parser.parse(file, null);
        assertThat(def.tools()).containsExactly("read", "grep", "bash");
    }

    @Test
    void qualifiedNameComposesNamespaceAndName(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("rev.md");
        Files.writeString(file, "---\nname: rev\n---\n\nbody");
        SubagentDefinition def = parser.parse(file, "myplugin");
        assertThat(def.qualifiedName()).isEqualTo("myplugin:rev");
    }

    @Test
    void splitFrontmatterHandlesMissingClosingDelimiter() {
        var fm = SubagentMarkdownParser.splitFrontmatter("---\nname: x\n\nno close");
        // No closing --- → entire thing treated as body, no frontmatter parsed.
        assertThat(fm.frontmatterYaml()).isNull();
    }

    @Test
    void splitFrontmatterHandlesContentWithoutFrontmatter() {
        var fm = SubagentMarkdownParser.splitFrontmatter("just plain markdown");
        assertThat(fm.frontmatterYaml()).isNull();
        assertThat(fm.body()).isEqualTo("just plain markdown");
    }
}
