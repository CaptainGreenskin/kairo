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
package io.kairo.core.guardrail.policy;

import io.kairo.api.guardrail.GuardrailContext;
import io.kairo.api.guardrail.GuardrailDecision;
import io.kairo.api.guardrail.GuardrailPayload;
import io.kairo.api.guardrail.GuardrailPhase;
import io.kairo.api.guardrail.GuardrailPolicy;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Mono;

/**
 * PRE_TOOL guardrail that rejects path-traversal ({@code ../}) attempts and writes outside an
 * optional allowed-root sandbox. Promoted from kairo-assistant in M-F5a; the file-tool name set was
 * widened to cover both kairo-tools (kairo-code uses {@code "read"} / {@code "write"} / {@code
 * "edit"}) and kairo-assistant (uses {@code "read_file"} / {@code "write_file"}).
 */
public class PathTraversalPolicy implements GuardrailPolicy {

    private static final String NAME = "PathTraversalPolicy";

    private static final List<String> PATH_ARG_KEYS =
            List.of("file_path", "filePath", "path", "directory", "dir", "target");

    /** Default file-tool names covering both kairo-tools and kairo-assistant naming conventions. */
    public static final Set<String> DEFAULT_FILE_TOOLS =
            Set.of(
                    "read",
                    "write",
                    "edit",
                    "glob",
                    "grep",
                    "patch",
                    "read_file",
                    "write_file",
                    "list_directory",
                    "search_files");

    private static final Set<String> SENSITIVE_PATHS =
            Set.of(
                    "/etc/passwd",
                    "/etc/shadow",
                    "/etc/sudoers",
                    "/root/.ssh",
                    "/root/.bashrc",
                    "/root/.bash_history");

    private final Path allowedRoot;
    private final Set<String> fileTools;

    public PathTraversalPolicy() {
        this(null, DEFAULT_FILE_TOOLS);
    }

    public PathTraversalPolicy(Path allowedRoot) {
        this(allowedRoot, DEFAULT_FILE_TOOLS);
    }

    public PathTraversalPolicy(Path allowedRoot, Set<String> fileTools) {
        this.allowedRoot = allowedRoot != null ? allowedRoot.toAbsolutePath().normalize() : null;
        this.fileTools =
                fileTools == null || fileTools.isEmpty()
                        ? DEFAULT_FILE_TOOLS
                        : Set.copyOf(fileTools);
    }

    @Override
    public Mono<GuardrailDecision> evaluate(GuardrailContext context) {
        if (context.phase() != GuardrailPhase.PRE_TOOL) {
            return Mono.just(GuardrailDecision.allow(NAME));
        }
        if (!(context.payload() instanceof GuardrailPayload.ToolInput toolInput)) {
            return Mono.just(GuardrailDecision.allow(NAME));
        }
        if (!fileTools.contains(toolInput.toolName())) {
            return Mono.just(GuardrailDecision.allow(NAME));
        }

        String filePath = extractPath(toolInput.args());
        if (filePath == null || filePath.isBlank()) {
            return Mono.just(GuardrailDecision.allow(NAME));
        }

        if (hasTraversalComponent(filePath)) {
            return Mono.just(
                    GuardrailDecision.deny("Path traversal detected in: " + filePath, NAME));
        }

        try {
            Path resolved = Path.of(filePath).toAbsolutePath().normalize();
            String normalizedStr = resolved.toString();

            for (String sensitive : SENSITIVE_PATHS) {
                if (normalizedStr.startsWith(sensitive)) {
                    return Mono.just(
                            GuardrailDecision.deny(
                                    "Access to sensitive path blocked: " + sensitive, NAME));
                }
            }

            if (allowedRoot != null && !resolved.startsWith(allowedRoot)) {
                return Mono.just(
                        GuardrailDecision.deny(
                                "Path outside allowed workspace: " + filePath, NAME));
            }
        } catch (InvalidPathException e) {
            return Mono.just(GuardrailDecision.deny("Invalid path: " + filePath, NAME));
        }
        return Mono.just(GuardrailDecision.allow(NAME));
    }

    @Override
    public int order() {
        return -85;
    }

    @Override
    public String name() {
        return NAME;
    }

    static boolean hasTraversalComponent(String path) {
        String[] segments = path.split("[/\\\\]");
        for (String segment : segments) {
            if ("..".equals(segment.trim())) return true;
        }
        return false;
    }

    private String extractPath(Map<String, Object> args) {
        if (args == null) return null;
        for (String key : PATH_ARG_KEYS) {
            Object val = args.get(key);
            if (val instanceof String s) return s;
        }
        return null;
    }
}
