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
package io.kairo.api.context;

/**
 * Configuration for context assembly behavior.
 *
 * <p>Controls how the {@link ContextBuilder} assembles and formats context entries before injection
 * into the LLM prompt.
 *
 * <p>Example:
 *
 * <pre>{@code
 * ContextBuilderConfig config = ContextBuilderConfig.builder()
 *     .sectionDelimiter("\n---\n")
 *     .maxEntries(10)
 *     .build();
 * }</pre>
 */
public class ContextBuilderConfig {

    /** Default delimiter between context sections. */
    public static final String DEFAULT_SECTION_DELIMITER = "\n\n";

    /** Default maximum number of entries (0 = unlimited). */
    public static final int DEFAULT_MAX_ENTRIES = 0;

    /** Default maximum character length per entry (0 = unlimited). */
    public static final int DEFAULT_MAX_ENTRY_LENGTH = 0;

    private final String sectionDelimiter;
    private final int maxEntries;
    private final int maxEntryLength;

    private ContextBuilderConfig(Builder builder) {
        this.sectionDelimiter = builder.sectionDelimiter;
        this.maxEntries = builder.maxEntries;
        this.maxEntryLength = builder.maxEntryLength;
    }

    /**
     * Delimiter string placed between assembled context sections.
     *
     * @return the delimiter (never null)
     */
    public String sectionDelimiter() {
        return sectionDelimiter;
    }

    /**
     * Maximum number of context entries to include. 0 means unlimited.
     *
     * @return the max entry count
     */
    public int maxEntries() {
        return maxEntries;
    }

    /**
     * Maximum character length per individual entry. 0 means unlimited. Entries exceeding this
     * limit will be truncated.
     *
     * @return the max entry length
     */
    public int maxEntryLength() {
        return maxEntryLength;
    }

    /**
     * Create a new builder with default values.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for ContextBuilderConfig. */
    public static class Builder {
        private String sectionDelimiter = DEFAULT_SECTION_DELIMITER;
        private int maxEntries = DEFAULT_MAX_ENTRIES;
        private int maxEntryLength = DEFAULT_MAX_ENTRY_LENGTH;

        private Builder() {}

        public Builder sectionDelimiter(String delimiter) {
            this.sectionDelimiter = delimiter;
            return this;
        }

        public Builder maxEntries(int max) {
            this.maxEntries = max;
            return this;
        }

        public Builder maxEntryLength(int max) {
            this.maxEntryLength = max;
            return this;
        }

        public ContextBuilderConfig build() {
            return new ContextBuilderConfig(this);
        }
    }

    /**
     * Returns a config with all default values.
     *
     * @return the default config
     */
    public static ContextBuilderConfig defaults() {
        return builder().build();
    }
}
