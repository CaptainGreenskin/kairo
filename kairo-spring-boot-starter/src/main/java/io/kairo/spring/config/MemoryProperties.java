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
package io.kairo.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Memory store configuration ({@code kairo.memory.*}).
 *
 * <p>Configures the backing store for agent memory (cross-session knowledge persistence). The
 * memory store is used by the Memory SPI to persist and retrieve knowledge entries.
 */
@ConfigurationProperties(prefix = "kairo.memory")
public class MemoryProperties {

    /**
     * Memory store type. Determines which storage backend is used.
     *
     * <p>Valid values: {@code "in-memory"} (non-persistent, lost on restart), {@code "file"} (JSON
     * files on disk), {@code "jdbc"} (relational database via JDBC).
     *
     * <p>Default: {@code "in-memory"}
     */
    private String type = "in-memory";

    /**
     * Store type alias (alternative to {@link #type}). Accepts the same values as {@code type}. If
     * set, takes precedence over {@code type}. Useful for Spring profiles where you want to
     * override the type without redefining all memory properties.
     *
     * <p>Default: {@code null} (defers to {@link #type})
     */
    private String storeType;

    /**
     * File store path for the {@code "file"} memory type. The directory where JSON memory files are
     * persisted.
     *
     * <p>Default: {@code null} (resolves to {@code ~/.kairo/memory})
     */
    private String fileStorePath;

    /**
     * Maximum number of entries to retain in the memory store. When exceeded, oldest entries are
     * evicted. Applies to all store types.
     *
     * <p>Valid range: 1–1,000,000
     *
     * <p>Default: {@code 10000}
     */
    private int maxEntries = 10000;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStoreType() {
        return storeType;
    }

    public void setStoreType(String storeType) {
        this.storeType = storeType;
    }

    public String getFileStorePath() {
        return fileStorePath;
    }

    public void setFileStorePath(String fileStorePath) {
        this.fileStorePath = fileStorePath;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    /**
     * Resolve the effective store type. {@link #storeType} takes precedence over {@link #type} when
     * both are set.
     *
     * @return the resolved store type identifier
     */
    public String resolveStoreType() {
        return (storeType != null && !storeType.isBlank()) ? storeType : type;
    }
}
