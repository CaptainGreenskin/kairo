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
package io.kairo.multiagent.orchestration.internal;

import io.kairo.api.team.SharedContext;
import io.kairo.api.team.TeamExecutionRequest;
import io.kairo.multiagent.orchestration.WorkspaceContextGatherer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Default implementation of {@link WorkspaceContextGatherer} that reads workspace structure and key
 * project files using blocking I/O on {@code Schedulers.boundedElastic()}.
 *
 * <p>This implementation:
 *
 * <ul>
 *   <li>Executes a depth-limited tree walk (prefers the {@code tree} command, falls back to Java
 *       {@link Files#walk})
 *   <li>Reads well-known project files (README, build descriptors, etc.)
 *   <li>Extracts a brief project summary from the first available descriptor
 * </ul>
 *
 * @since v0.11 (Experimental)
 */
public final class DefaultWorkspaceContextGatherer implements WorkspaceContextGatherer {

    private static final Logger log =
            LoggerFactory.getLogger(DefaultWorkspaceContextGatherer.class);

    private static final int DEFAULT_TREE_DEPTH = 3;
    private static final int DEFAULT_MAX_KEY_FILE_CHARS = 4000;
    private static final int MAX_TREE_CHARS = 8000;
    private static final int MAX_SUMMARY_CHARS = 500;

    private static final List<String> KEY_FILE_NAMES =
            List.of(
                    "README.md",
                    "README",
                    "pom.xml",
                    "build.gradle",
                    "build.gradle.kts",
                    "package.json",
                    "Cargo.toml",
                    "go.mod",
                    "pyproject.toml",
                    "Makefile",
                    "docker-compose.yml",
                    ".gitignore");

    private final Path workspaceRoot;
    private final int treeDepth;
    private final int maxKeyFileChars;

    /**
     * Creates a gatherer with all configurable parameters.
     *
     * @param workspaceRoot root directory of the workspace; must not be null
     * @param treeDepth maximum depth for the tree walk; must be positive
     * @param maxKeyFileChars maximum characters to read per key file; must be positive
     */
    public DefaultWorkspaceContextGatherer(Path workspaceRoot, int treeDepth, int maxKeyFileChars) {
        this.workspaceRoot =
                Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null");
        if (treeDepth < 1) {
            throw new IllegalArgumentException("treeDepth must be positive, got: " + treeDepth);
        }
        if (maxKeyFileChars < 1) {
            throw new IllegalArgumentException(
                    "maxKeyFileChars must be positive, got: " + maxKeyFileChars);
        }
        this.treeDepth = treeDepth;
        this.maxKeyFileChars = maxKeyFileChars;
    }

    /**
     * Creates a gatherer with default depth (3) and max key file chars (4000).
     *
     * @param workspaceRoot root directory of the workspace
     */
    public DefaultWorkspaceContextGatherer(Path workspaceRoot) {
        this(workspaceRoot, DEFAULT_TREE_DEPTH, DEFAULT_MAX_KEY_FILE_CHARS);
    }

    @Override
    public Mono<SharedContext> gather(TeamExecutionRequest request) {
        return Mono.fromCallable(this::gatherBlocking).subscribeOn(Schedulers.boundedElastic());
    }

    private SharedContext gatherBlocking() {
        String tree = executeTree(workspaceRoot, treeDepth);
        Map<String, String> keyFiles = readKeyFiles(workspaceRoot);
        String summary = buildProjectSummary(workspaceRoot, keyFiles);

        return new SharedContext.Builder()
                .workspaceTree(tree)
                .keyFiles(keyFiles)
                .projectSummary(summary)
                .metadata(Map.of())
                .gatheredAt(Instant.now())
                .build();
    }

    /**
     * Execute a depth-limited tree listing of the workspace. Tries the native {@code tree} command
     * first; falls back to Java file walk.
     */
    String executeTree(Path root, int depth) {
        try {
            return executeTreeCommand(root, depth);
        } catch (Exception e) {
            log.debug(
                    "Native 'tree' command unavailable, falling back to Java file walk: {}",
                    e.getMessage());
            return executeJavaTreeFallback(root, depth);
        }
    }

    private String executeTreeCommand(Path root, int depth)
            throws IOException, InterruptedException {
        ProcessBuilder pb =
                new ProcessBuilder(
                        "tree", "-L", String.valueOf(depth), "--noreport", root.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output;
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().collect(Collectors.joining("\n"));
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("tree command exited with code " + exitCode);
        }

        return truncate(output, MAX_TREE_CHARS);
    }

    private String executeJavaTreeFallback(Path root, int depth) {
        StringBuilder sb = new StringBuilder();
        sb.append(root.getFileName()).append('\n');

        try (Stream<Path> walk = Files.walk(root, depth)) {
            walk.filter(p -> !p.equals(root))
                    .forEach(
                            p -> {
                                int level = root.relativize(p).getNameCount();
                                String indent = "  ".repeat(level - 1);
                                String prefix = Files.isDirectory(p) ? "├── " : "├── ";
                                sb.append(indent)
                                        .append(prefix)
                                        .append(p.getFileName())
                                        .append('\n');
                            });
        } catch (IOException e) {
            log.warn("Failed to walk workspace directory: {}", e.getMessage());
            return "(workspace tree unavailable)";
        }

        return truncate(sb.toString(), MAX_TREE_CHARS);
    }

    /**
     * Read well-known project files from the workspace root.
     *
     * @return map of relative path → file content (truncated)
     */
    Map<String, String> readKeyFiles(Path root) {
        Map<String, String> result = new LinkedHashMap<>();

        for (String fileName : KEY_FILE_NAMES) {
            Path filePath = root.resolve(fileName);
            if (Files.isRegularFile(filePath)) {
                try {
                    String content = Files.readString(filePath, StandardCharsets.UTF_8);
                    result.put(fileName, truncate(content, maxKeyFileChars));
                } catch (IOException e) {
                    log.warn("Failed to read key file '{}': {}", fileName, e.getMessage());
                }
            }
        }

        return result;
    }

    /** Build a brief project summary from available key files. */
    String buildProjectSummary(Path root, Map<String, String> keyFiles) {
        // Try README first
        String readme = keyFiles.get("README.md");
        if (readme == null) {
            readme = keyFiles.get("README");
        }
        if (readme != null) {
            return extractFirstParagraph(readme);
        }

        // Try pom.xml
        String pom = keyFiles.get("pom.xml");
        if (pom != null) {
            return extractPomSummary(pom);
        }

        // Try package.json
        String packageJson = keyFiles.get("package.json");
        if (packageJson != null) {
            return extractPackageJsonSummary(packageJson);
        }

        return "";
    }

    private String extractFirstParagraph(String readme) {
        // Skip leading blank lines and headers, take first paragraph
        String[] lines = readme.split("\n");
        StringBuilder paragraph = new StringBuilder();
        boolean started = false;

        for (String line : lines) {
            if (!started) {
                // Skip empty lines and markdown header lines at the start
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                started = true;
            }

            if (started) {
                if (line.isBlank()) {
                    break; // end of first paragraph
                }
                if (paragraph.length() > 0) {
                    paragraph.append(' ');
                }
                paragraph.append(line.trim());
            }

            if (paragraph.length() >= MAX_SUMMARY_CHARS) {
                break;
            }
        }

        return truncate(paragraph.toString(), MAX_SUMMARY_CHARS);
    }

    private String extractPomSummary(String pom) {
        String groupId = extractXmlElement(pom, "groupId");
        String artifactId = extractXmlElement(pom, "artifactId");
        String description = extractXmlElement(pom, "description");

        StringBuilder sb = new StringBuilder();
        if (groupId != null) {
            sb.append(groupId);
        }
        if (artifactId != null) {
            if (sb.length() > 0) sb.append(':');
            sb.append(artifactId);
        }
        if (description != null) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(description);
        }

        return truncate(sb.toString(), MAX_SUMMARY_CHARS);
    }

    private String extractXmlElement(String xml, String element) {
        String open = "<" + element + ">";
        String close = "</" + element + ">";
        int start = xml.indexOf(open);
        if (start < 0) return null;
        start += open.length();
        int end = xml.indexOf(close, start);
        if (end < 0) return null;
        return xml.substring(start, end).trim();
    }

    private String extractPackageJsonSummary(String json) {
        String name = extractJsonStringField(json, "name");
        String description = extractJsonStringField(json, "description");

        StringBuilder sb = new StringBuilder();
        if (name != null) {
            sb.append(name);
        }
        if (description != null) {
            if (sb.length() > 0) sb.append(" - ");
            sb.append(description);
        }

        return truncate(sb.toString(), MAX_SUMMARY_CHARS);
    }

    private String extractJsonStringField(String json, String field) {
        // Simple extraction for "field": "value" patterns
        String pattern = "\"" + field + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx < 0) return null;
        int quoteStart = json.indexOf('"', colonIdx + 1);
        if (quoteStart < 0) return null;
        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteEnd < 0) return null;
        return json.substring(quoteStart + 1, quoteEnd);
    }

    private static String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxChars) + "...(truncated)";
    }
}
