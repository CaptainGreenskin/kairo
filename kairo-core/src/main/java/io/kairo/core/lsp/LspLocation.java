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

/**
 * A location in a source file returned by LSP operations.
 *
 * @param uri the file URI (e.g., "file:///path/to/File.java")
 * @param startLine zero-based start line
 * @param startCharacter zero-based start column
 * @param endLine zero-based end line
 * @param endCharacter zero-based end column
 */
public record LspLocation(
        String uri, int startLine, int startCharacter, int endLine, int endCharacter) {

    public String toHumanReadable() {
        String path = uri.startsWith("file://") ? uri.substring(7) : uri;
        return path + ":" + (startLine + 1) + ":" + (startCharacter + 1);
    }
}
