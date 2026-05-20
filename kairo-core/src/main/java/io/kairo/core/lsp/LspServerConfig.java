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
package io.kairo.core.lsp;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration for a language server.
 *
 * @param languageId the LSP language identifier (e.g., "java", "typescript", "python")
 * @param command the command and arguments to start the server
 * @param fileExtensions file extensions this server handles (e.g., ".java", ".ts")
 */
public record LspServerConfig(String languageId, List<String> command, Set<String> fileExtensions) {

    public LspServerConfig {
        Objects.requireNonNull(languageId, "languageId must not be null");
        command = List.copyOf(Objects.requireNonNull(command, "command must not be null"));
        fileExtensions =
                Set.copyOf(
                        Objects.requireNonNull(fileExtensions, "fileExtensions must not be null"));
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
    }

    public static LspServerConfig java(String jdtlsPath) {
        return new LspServerConfig("java", List.of(jdtlsPath, "--stdio"), Set.of(".java"));
    }

    public static LspServerConfig typescript() {
        return new LspServerConfig(
                "typescript",
                List.of("typescript-language-server", "--stdio"),
                Set.of(".ts", ".tsx", ".js", ".jsx"));
    }

    public static LspServerConfig python() {
        return new LspServerConfig("python", List.of("pylsp"), Set.of(".py"));
    }
}
