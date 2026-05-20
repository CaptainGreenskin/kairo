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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.tool.ToolCategory;
import io.kairo.api.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ToolRelevanceScorerTest {

    private static ToolDefinition tool(String name, String description) {
        return new ToolDefinition(
                name, description, ToolCategory.GENERAL, null, ToolRelevanceScorerTest.class);
    }

    @Test
    void exactMatchScoresHigh() {
        ToolDefinition bashTool = tool("bash", "Execute shell commands and scripts");
        ToolDefinition readTool = tool("read", "Read a file from disk");

        List<ToolDefinition> all = List.of(bashTool, readTool);

        double bashScore = ToolRelevanceScorer.score(bashTool, "execute shell command", all);
        double readScore = ToolRelevanceScorer.score(readTool, "execute shell command", all);

        assertThat(bashScore).isGreaterThan(readScore);
        assertThat(bashScore).isGreaterThan(0.3);
    }

    @Test
    void noOverlapScoresZero() {
        ToolDefinition tool = tool("web_search", "Search the web for information");
        List<ToolDefinition> all = List.of(tool);

        double score = ToolRelevanceScorer.score(tool, "compile java code", all);
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void emptyQueryScoresZero() {
        ToolDefinition tool = tool("bash", "Execute commands");
        assertThat(ToolRelevanceScorer.score(tool, "", List.of(tool))).isEqualTo(0.0);
        assertThat(ToolRelevanceScorer.score(tool, null, List.of(tool))).isEqualTo(0.0);
    }

    @Test
    void idfBoostsRareTerms() {
        ToolDefinition searchTool = tool("web_search", "Search the web for documentation");
        ToolDefinition readTool = tool("read_file", "Read a file from the filesystem");
        ToolDefinition editTool = tool("edit_file", "Edit a file on the filesystem");

        List<ToolDefinition> all = List.of(searchTool, readTool, editTool);

        // "web" is unique to searchTool; "file" appears in both read and edit
        double searchScore = ToolRelevanceScorer.score(searchTool, "web documentation", all);
        double readScore = ToolRelevanceScorer.score(readTool, "file read", all);

        // searchTool should score high because "web" is rare
        assertThat(searchScore).isGreaterThan(0.3);
        assertThat(readScore).isGreaterThan(0.0);
    }

    @Test
    void scoreSimpleNoIdf() {
        ToolDefinition tool = tool("bash", "Execute shell commands and scripts");

        double score = ToolRelevanceScorer.scoreSimple(tool, "execute shell");
        assertThat(score).isGreaterThan(0.5);
    }

    @Test
    void scoreSimpleEmptyQuery() {
        ToolDefinition tool = tool("bash", "Execute commands");
        assertThat(ToolRelevanceScorer.scoreSimple(tool, "")).isEqualTo(0.0);
    }

    @Test
    void tokenizeSplitsOnPunctuation() {
        Set<String> tokens = ToolRelevanceScorer.tokenize("hello, world! foo-bar");
        assertThat(tokens).contains("hello", "world", "foo", "bar");
    }

    @Test
    void tokenizeHandlesNull() {
        assertThat(ToolRelevanceScorer.tokenize(null)).isEmpty();
        assertThat(ToolRelevanceScorer.tokenize("")).isEmpty();
    }

    @Test
    void buildSearchTextIncludesNameAndDescription() {
        ToolDefinition tool =
                new ToolDefinition(
                        "web_search",
                        "Search the internet",
                        ToolCategory.INFORMATION,
                        null,
                        ToolRelevanceScorerTest.class,
                        null,
                        null,
                        "Use for current events");
        String text = ToolRelevanceScorer.buildSearchText(tool);
        assertThat(text).contains("web");
        assertThat(text).contains("search");
        assertThat(text).contains("internet");
        assertThat(text).contains("current events");
    }

    @Test
    void computeIdfHigherForRareTerms() {
        ToolDefinition t1 = tool("a", "file system read");
        ToolDefinition t2 = tool("b", "file system write");
        ToolDefinition t3 = tool("c", "web search documentation");

        List<ToolDefinition> all = List.of(t1, t2, t3);
        Set<String> queryTerms = Set.of("file", "web");

        Map<String, Double> idf = ToolRelevanceScorer.computeIdf(queryTerms, all);

        // "file" appears in 2/3 tools, "web" appears in 1/3 → "web" has higher IDF
        assertThat(idf.get("web")).isGreaterThan(idf.get("file"));
    }

    @Test
    void nameUnderscoresConvertedToSpaces() {
        ToolDefinition tool = tool("web_search_advanced", "Find things online");
        String text = ToolRelevanceScorer.buildSearchText(tool);
        assertThat(text).contains("web search advanced");
    }
}
