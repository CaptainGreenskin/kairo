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
package io.kairo.core.context.recovery;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Tracks recently accessed files for post-compaction recovery.
 *
 * <p>Maintains an LRU list of the 5 most recently accessed file paths. After compaction, these
 * files can be re-read to restore critical context that was lost during compression.
 */
public class FileAccessTracker {

    private static final int MAX_TRACKED = 5;
    private final LinkedList<String> recentFiles = new LinkedList<>();

    /**
     * Record a file access. Moves the file to the front if already tracked.
     *
     * @param filePath the absolute path of the accessed file
     */
    public synchronized void recordAccess(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        recentFiles.remove(filePath); // remove if exists (O(n) but n≤5)
        recentFiles.addFirst(filePath);
        if (recentFiles.size() > MAX_TRACKED) {
            recentFiles.removeLast();
        }
    }

    /**
     * Get recently accessed files, most recent first.
     *
     * @return a defensive copy of the recent files list
     */
    public synchronized List<String> getRecentFiles() {
        return new ArrayList<>(recentFiles);
    }

    /** Clear all tracked files. */
    public synchronized void clear() {
        recentFiles.clear();
    }
}
