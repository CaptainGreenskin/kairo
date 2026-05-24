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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowParserTest {

    @Test
    void parsesMinimalWorkflow() throws Exception {
        String yaml =
                """
                name: hello
                steps:
                  - name: greet
                    tool: bash
                """;
        WorkflowDefinition wf = WorkflowParser.fromYaml(yaml);
        assertThat(wf.name()).isEqualTo("hello");
        assertThat(wf.steps()).hasSize(1);
        assertThat(wf.steps().get(0).name()).isEqualTo("greet");
        assertThat(wf.steps().get(0).tool()).isEqualTo("bash");
        assertThat(wf.steps().get(0).args()).isEmpty();
        assertThat(wf.steps().get(0).continueOnError()).isFalse();
    }

    @Test
    void parsesArgsAsMap() throws Exception {
        String yaml =
                """
                name: argy
                steps:
                  - name: run
                    tool: bash
                    args:
                      command: "echo hi"
                      timeoutSeconds: 30
                """;
        WorkflowDefinition wf = WorkflowParser.fromYaml(yaml);
        assertThat(wf.steps().get(0).args())
                .containsEntry("command", "echo hi")
                .containsEntry("timeoutSeconds", 30);
    }

    @Test
    void parsesContinueOnError() throws Exception {
        String yaml =
                """
                name: tolerant
                steps:
                  - name: best-effort
                    tool: bash
                    continue_on_error: true
                """;
        WorkflowDefinition wf = WorkflowParser.fromYaml(yaml);
        assertThat(wf.steps().get(0).continueOnError()).isTrue();
    }

    @Test
    void rejectsMissingName() {
        String yaml =
                """
                steps:
                  - name: step
                    tool: bash
                """;
        assertThatThrownBy(() -> WorkflowParser.fromYaml(yaml))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("workflow 'name'");
    }

    @Test
    void rejectsEmptySteps() {
        String yaml =
                """
                name: empty
                steps: []
                """;
        assertThatThrownBy(() -> WorkflowParser.fromYaml(yaml))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("non-empty");
    }

    @Test
    void rejectsStepMissingTool() {
        String yaml =
                """
                name: bad
                steps:
                  - name: notool
                """;
        assertThatThrownBy(() -> WorkflowParser.fromYaml(yaml))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("'tool'");
    }

    @Test
    void parsesJsonFile(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("wf.json");
        Files.writeString(f, "{\"name\":\"jw\",\"steps\":[{\"name\":\"s1\",\"tool\":\"bash\"}]}");
        WorkflowDefinition wf = WorkflowParser.fromFile(f);
        assertThat(wf.name()).isEqualTo("jw");
        assertThat(wf.steps()).hasSize(1);
    }

    @Test
    void fromFile_missingFile_throws() {
        Path nope = Path.of("/tmp/definitely-not-a-real-workflow-file-12345.yaml");
        assertThatThrownBy(() -> WorkflowParser.fromFile(nope)).isInstanceOf(IOException.class);
    }
}
