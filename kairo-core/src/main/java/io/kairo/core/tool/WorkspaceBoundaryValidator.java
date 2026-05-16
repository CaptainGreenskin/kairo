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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Utility class that validates file paths remain within a designated workspace boundary.
 *
 * <p>Prevents path-traversal attacks (e.g. {@code ../../../etc/passwd}) by resolving and
 * normalizing candidate paths and verifying they stay under the workspace root.
 *
 * @since 1.3.0
 */
public final class WorkspaceBoundaryValidator {

    private WorkspaceBoundaryValidator() {
        // utility class — no instantiation
    }

    /**
     * Validates that a file path resolves within the workspace boundary.
     *
     * @param filePath the candidate file path to validate
     * @param workspaceRoot the workspace root directory
     * @return {@link Optional#of(Object)} with an error message if the path escapes the workspace;
     *     {@link Optional#empty()} if safe
     */
    public static Optional<String> validate(String filePath, Path workspaceRoot) {
        if (filePath == null || filePath.isBlank()) {
            return Optional.empty();
        }
        Path resolved = workspaceRoot.resolve(filePath).toAbsolutePath().normalize();
        Path normalRoot = workspaceRoot.toAbsolutePath().normalize();
        if (!resolved.startsWith(normalRoot)) {
            return Optional.of(
                    "Path traversal blocked: '"
                            + filePath
                            + "' resolves outside workspace boundary ("
                            + normalRoot
                            + ")");
        }
        return Optional.empty();
    }

    /**
     * Extracts path-like arguments from tool input by convention.
     *
     * <p>Checks common parameter names ({@code path}, {@code filePath}, {@code targetPath}) and
     * items in a {@code files} list (BatchWriteTool convention).
     *
     * @param args the tool argument map
     * @return a list of extracted path strings (never null, may be empty)
     */
    public static List<String> extractPaths(Map<String, Object> args) {
        List<String> paths = new ArrayList<>();

        // Check common path param names
        for (String key : List.of("path", "filePath", "targetPath")) {
            Object val = args.get(key);
            if (val instanceof String s && !s.isBlank()) {
                paths.add(s);
            }
        }

        // Check files list (BatchWriteTool convention)
        Object files = args.get("files");
        if (files instanceof List<?> fileList) {
            for (Object item : fileList) {
                if (item instanceof Map<?, ?> m) {
                    Object p = m.get("path");
                    if (p instanceof String s && !s.isBlank()) {
                        paths.add(s);
                    }
                }
            }
        }

        return paths;
    }
}
