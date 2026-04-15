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
package io.kairo.core.context.source;

import io.kairo.api.context.ContextSource;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Provides a summary of the project directory structure as context.
 *
 * <p>Scans the working directory and renders a tree-like view of the top-level structure (up to 2
 * levels deep). Directories and common project files are shown.
 *
 * <p>Output example:
 *
 * <pre>
 * Project structure:
 * ├── src/
 * │   ├── main/
 * │   └── test/
 * ├── pom.xml
 * └── README.md
 * </pre>
 *
 * <p>Priority 20 — useful for project-aware reasoning but not critical.
 */
public class ProjectContextSource implements ContextSource {

    private static final int MAX_ENTRIES = 40;
    private static final int MAX_DEPTH = 2;

    private volatile String cached;

    @Override
    public String getName() {
        return "project-structure";
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public String collect() {
        if (cached != null) {
            return cached;
        }
        String structure = buildStructure();
        cached = structure;
        return structure;
    }

    private String buildStructure() {
        String userDir = System.getProperty("user.dir");
        if (userDir == null) {
            return "";
        }

        File root = new File(userDir);
        if (!root.isDirectory()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Project structure (").append(root.getName()).append("):\n");

        List<String> lines = new ArrayList<>();
        int[] count = {0};
        walkTree(root, "", true, lines, count, 0);

        for (String line : lines) {
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }

    private void walkTree(
            File dir, String prefix, boolean isLast, List<String> lines, int[] count, int depth) {
        if (depth > MAX_DEPTH || count[0] >= MAX_ENTRIES) {
            return;
        }

        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }

        // Sort: directories first, then files, alphabetically
        Arrays.sort(
                children,
                Comparator.comparingInt((File f) -> f.isDirectory() ? 0 : 1)
                        .thenComparing(File::getName));

        for (int i = 0; i < children.length && count[0] < MAX_ENTRIES; i++) {
            File child = children[i];
            boolean childIsLast = (i == children.length - 1);
            String connector = childIsLast ? "└── " : "├── ";

            if (count[0] > 0 || depth > 0) {
                lines.add(prefix + connector + child.getName() + (child.isDirectory() ? "/" : ""));
            } else {
                lines.add(connector + child.getName() + (child.isDirectory() ? "/" : ""));
            }
            count[0]++;

            if (child.isDirectory() && depth < MAX_DEPTH) {
                String childPrefix = prefix + (childIsLast ? "    " : "│   ");
                walkTree(child, childPrefix, childIsLast, lines, count, depth + 1);
            }
        }

        if (count[0] >= MAX_ENTRIES && depth == 0) {
            lines.add("... (truncated)");
        }
    }
}
