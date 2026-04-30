package io.kairo.tools.file;

import static org.junit.jupiter.api.Assertions.*;

import io.kairo.api.tool.ToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateRenderToolTest {

    private TemplateRenderTool tool;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new TemplateRenderTool();
    }

    @Test
    void simpleVariableReplacement() throws IOException {
        Path templateFile = tempDir.resolve("template.txt");
        Files.writeString(templateFile, "Hello, {{name}}! You are {{age}} years old.");

        ToolResult result =
                tool.execute(
                        Map.of(
                                "template",
                                templateFile.toString(),
                                "variables",
                                "{\"name\": \"Alice\", \"age\": 30}"));

        assertFalse(result.isError());
        assertEquals("Hello, Alice! You are 30 years old.", result.content());
    }

    @Test
    void rawVariableNoHtmlEscape() throws IOException {
        Path templateFile = tempDir.resolve("template.txt");
        Files.writeString(templateFile, "Content: {{{html}}}");

        ToolResult result =
                tool.execute(
                        Map.of(
                                "template",
                                templateFile.toString(),
                                "variables",
                                "{\"html\": \"<b>bold</b>\"}"));

        assertFalse(result.isError());
        assertEquals("Content: <b>bold</b>", result.content());
    }

    @Test
    void escapedVariableHtmlEntities() throws IOException {
        Path templateFile = tempDir.resolve("template.txt");
        Files.writeString(templateFile, "Safe: {{html}}");

        ToolResult result =
                tool.execute(
                        Map.of(
                                "template",
                                templateFile.toString(),
                                "variables",
                                "{\"html\": \"<script>&\\\"test\\\"</script>\"}"));

        assertFalse(result.isError());
        assertEquals("Safe: &lt;script&gt;&amp;&quot;test&quot;&lt;/script&gt;", result.content());
    }

    @Test
    void truthySectionRendersBody() throws IOException {
        Path templateFile = tempDir.resolve("template.txt");
        Files.writeString(templateFile, "Start{{#show}} visible{{/show}}End");

        ToolResult result =
                tool.execute(
                        Map.of(
                                "template",
                                templateFile.toString(),
                                "variables",
                                "{\"show\": true}"));

        assertFalse(result.isError());
        assertEquals("Start visibleEnd", result.content());
    }

    @Test
    void truthySectionWithObjectRendersBody() throws IOException {
        Path templateFile = tempDir.resolve("template.txt");
        Files.writeString(templateFile, "{{#user}}Name: {{name}}, Age: {{age}}{{/user}}");

        ToolResult result =
                tool.execute(
                        Map.of(
                                "template",
                                templateFile.toString(),
                                "variables",
                                "{\"user\": {\"name\": \"Bob\", \"age\": 25}}"));

        assertFalse(result.isError());
        assertEquals("Name: Bob, Age: 25", result.content());
    }

    @Test
    void arraySectionIteratesElements() throws IOException {
        Path templateFile = tempDir.resolve("template.txt");
        Files.writeString(templateFile, "Items:{{#items}} [{{.}}]{{/items}}");

        ToolResult result =
                tool.execute(
                        Map.of(
                                "template",
                                templateFile.toString(),
                                "variables",
                                "{\"items\": [\"a\", \"b\", \"c\"]}"));

        assertFalse(result.isError());
        assertEquals("Items: [a] [b] [c]", result.content());
    }

    @Test
    void arraySectionWithObjectElements() throws IOException {
        Path templateFile = tempDir.resolve("template.txt");
        Files.writeString(templateFile, "<ul>{{#users}}<li>{{name}}</li>{{/users}}</ul>");

        ToolResult result =
                tool.execute(
                        Map.of(
                                "template",
                                templateFile.toString(),
                                "variables",
                                "{\"users\": [{\"name\": \"Alice\"}, {\"name\": \"Bob\"}]}"));

        assertFalse(result.isError());
        assertEquals("<ul><li>Alice</li><li>Bob</li></ul>", result.content());
    }

    @Test
    void invertedSectionRendersWhenFalsy() throws IOException {
        Path templateFile = tempDir.resolve("template.txt");
        Files.writeString(templateFile, "{{^hidden}}This is visible{{/hidden}}");

        ToolResult result =
                tool.execute(
                        Map.of(
                                "template",
                                templateFile.toString(),
                                "variables",
                                "{\"hidden\": false}"));

        assertFalse(result.isError());
        assertEquals("This is visible", result.content());
    }

    @Test
    void invertedSectionSkippedWhenTruthy() throws IOException {
        Path templateFile = tempDir.resolve("template.txt");
        Files.writeString(templateFile, "{{^show}}Not rendered{{/show}}Done");

        ToolResult result =
                tool.execute(
                        Map.of(
                                "template",
                                templateFile.toString(),
                                "variables",
                                "{\"show\": true}"));

        assertFalse(result.isError());
        assertEquals("Done", result.content());
    }

    @Test
    void invertedSectionRendersWhenKeyMissing() throws IOException {
        Path templateFile = tempDir.resolve("template.txt");
        Files.writeString(templateFile, "{{^missing}}Fallback content{{/missing}}");

        ToolResult result =
                tool.execute(Map.of("template", templateFile.toString(), "variables", "{}"));

        assertFalse(result.isError());
        assertEquals("Fallback content", result.content());
    }

    @Test
    void commentNotInOutput() throws IOException {
        Path templateFile = tempDir.resolve("template.txt");
        Files.writeString(templateFile, "Before{{! this is a comment }}After");

        ToolResult result =
                tool.execute(Map.of("template", templateFile.toString(), "variables", "{}"));

        assertFalse(result.isError());
        assertEquals("BeforeAfter", result.content());
    }

    @Test
    void templateFileNotFound() {
        ToolResult result =
                tool.execute(
                        Map.of(
                                "template",
                                tempDir.resolve("nonexistent.txt").toString(),
                                "variables",
                                "{}"));

        assertTrue(result.isError());
    }

    @Test
    void missingVariablesParameter() throws IOException {
        Path templateFile = tempDir.resolve("template.txt");
        Files.writeString(templateFile, "Hello {{name}}");

        ToolResult result = tool.execute(Map.of("template", templateFile.toString()));

        assertTrue(result.isError());
    }

    @Test
    void outputPathWritesFile() throws IOException {
        Path templateFile = tempDir.resolve("template.txt");
        Path outputFile = tempDir.resolve("output.txt");
        Files.writeString(templateFile, "Result: {{value}}");

        ToolResult result =
                tool.execute(
                        Map.of(
                                "template", templateFile.toString(),
                                "variables", "{\"value\": \"42\"}",
                                "outputPath", outputFile.toString()));

        assertFalse(result.isError());
        assertEquals("Result: 42", result.content());
        assertEquals("Result: 42", Files.readString(outputFile));
        assertTrue(result.metadata().containsKey("outputPath"));
    }

    @Test
    void metadataContainsLinesRendered() throws IOException {
        Path templateFile = tempDir.resolve("template.txt");
        Files.writeString(templateFile, "Line1: {{a}}\nLine2: {{b}}");

        ToolResult result =
                tool.execute(
                        Map.of(
                                "template",
                                templateFile.toString(),
                                "variables",
                                "{\"a\": \"x\", \"b\": \"y\"}"));

        assertFalse(result.isError());
        assertTrue(result.metadata().containsKey("linesRendered"));
    }

    @Test
    void inlineTemplateString() {
        ToolResult result =
                tool.execute(
                        Map.of(
                                "template", "Hello {{name}}!",
                                "variables", "{\"name\": \"World\"}"));

        assertFalse(result.isError());
        assertEquals("Hello World!", result.content());
    }
}
