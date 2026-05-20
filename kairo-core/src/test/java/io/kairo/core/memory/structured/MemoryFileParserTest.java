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
package io.kairo.core.memory.structured;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MemoryFileParserTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-05-15T10:00:00Z");

    @Test
    void parseValidFrontmatterAndBody() {
        String markdown =
                """
                ---
                name: feedback-testing
                description: Integration tests must hit real database
                metadata:
                  type: feedback
                ---

                Integration tests must hit a real database.
                **Why:** prior incident where mock/prod divergence masked a broken migration.
                """;

        MemoryFile file = MemoryFileParser.parse(markdown, FIXED_TIME);

        assertThat(file.name()).isEqualTo("feedback-testing");
        assertThat(file.description()).isEqualTo("Integration tests must hit real database");
        assertThat(file.type()).isEqualTo(MemoryType.FEEDBACK);
        assertThat(file.body()).contains("Integration tests must hit a real database.");
        assertThat(file.body()).contains("**Why:**");
        assertThat(file.updatedAt()).isEqualTo(FIXED_TIME);
    }

    @Test
    void parseTypeCaseInsensitive() {
        String markdown =
                """
                ---
                name: user-role
                description: User is a data scientist
                metadata:
                  type: USER
                ---

                The user is a data scientist.
                """;

        MemoryFile file = MemoryFileParser.parse(markdown, FIXED_TIME);
        assertThat(file.type()).isEqualTo(MemoryType.USER);
    }

    @Test
    void parseAllTypes() {
        for (MemoryType type : MemoryType.values()) {
            String markdown =
                    """
                    ---
                    name: test-%s
                    description: test
                    metadata:
                      type: %s
                    ---

                    body
                    """
                            .formatted(type.name().toLowerCase(), type.name().toLowerCase());

            MemoryFile file = MemoryFileParser.parse(markdown, FIXED_TIME);
            assertThat(file.type()).isEqualTo(type);
        }
    }

    @Test
    void parseMetadataOnly() {
        String markdown =
                """
                ---
                name: project-direction
                description: SPI-governance framework
                metadata:
                  type: project
                ---

                Long body content that should be ignored for metadata-only parsing.
                """;

        MemoryFile file = MemoryFileParser.parseMetadataOnly(markdown);
        assertThat(file.name()).isEqualTo("project-direction");
        assertThat(file.description()).isEqualTo("SPI-governance framework");
        assertThat(file.type()).isEqualTo(MemoryType.PROJECT);
        assertThat(file.body()).isEmpty();
    }

    @Test
    void serializeRoundTrip() {
        MemoryFile original =
                new MemoryFile(
                        "feedback-testing",
                        "Integration tests must hit real database",
                        MemoryType.FEEDBACK,
                        "The body content.\n**Why:** reasons.",
                        FIXED_TIME);

        String serialized = MemoryFileParser.serialize(original);
        MemoryFile parsed = MemoryFileParser.parse(serialized, FIXED_TIME);

        assertThat(parsed.name()).isEqualTo(original.name());
        assertThat(parsed.description()).isEqualTo(original.description());
        assertThat(parsed.type()).isEqualTo(original.type());
        assertThat(parsed.body()).isEqualTo(original.body());
    }

    @Test
    void serializeEmptyBody() {
        MemoryFile file =
                new MemoryFile(
                        "ref-linear",
                        "Bugs tracked in Linear",
                        MemoryType.REFERENCE,
                        "",
                        FIXED_TIME);

        String serialized = MemoryFileParser.serialize(file);
        assertThat(serialized).contains("name: ref-linear");
        assertThat(serialized).contains("type: reference");
        assertThat(serialized).doesNotContain("\n\n\n");
    }

    @Test
    void rejectsEmptyContent() {
        assertThatThrownBy(() -> MemoryFileParser.parse(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsNullContent() {
        assertThatThrownBy(() -> MemoryFileParser.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsMissingOpeningDelimiter() {
        assertThatThrownBy(() -> MemoryFileParser.parse("no frontmatter here"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delimiter");
    }

    @Test
    void rejectsMissingClosingDelimiter() {
        assertThatThrownBy(
                        () ->
                                MemoryFileParser.parse(
                                        """
                ---
                name: test
                description: test
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closing");
    }

    @Test
    void rejectsMissingName() {
        assertThatThrownBy(
                        () ->
                                MemoryFileParser.parse(
                                        """
                ---
                description: test
                metadata:
                  type: user
                ---
                body
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void rejectsMissingDescription() {
        assertThatThrownBy(
                        () ->
                                MemoryFileParser.parse(
                                        """
                ---
                name: test
                metadata:
                  type: user
                ---
                body
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("description");
    }

    @Test
    void rejectsMissingMetadataType() {
        assertThatThrownBy(
                        () ->
                                MemoryFileParser.parse(
                                        """
                ---
                name: test
                description: test
                ---
                body
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metadata.type");
    }

    @Test
    void rejectsInvalidType() {
        assertThatThrownBy(
                        () ->
                                MemoryFileParser.parse(
                                        """
                ---
                name: test
                description: test
                metadata:
                  type: invalid_type
                ---
                body
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid memory type");
    }
}
