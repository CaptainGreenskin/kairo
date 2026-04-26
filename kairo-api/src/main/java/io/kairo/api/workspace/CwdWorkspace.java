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
package io.kairo.api.workspace;

import java.nio.file.Path;
import java.util.Map;

/**
 * Default {@link Workspace} rooted at the JVM's current working directory.
 *
 * <p>Resolved once at class init from {@code Path.of("").toAbsolutePath().normalize()}. Lives in
 * kairo-api so the SPI can offer a no-dependency default — production callers should use {@code
 * LocalDirectoryWorkspaceProvider} (kairo-core) for explicit control over the base directory.
 */
final class CwdWorkspace implements Workspace {

    static final CwdWorkspace INSTANCE = new CwdWorkspace();

    private final Path root = Path.of("").toAbsolutePath().normalize();
    private final String id = "local:" + root;

    private CwdWorkspace() {}

    @Override
    public String id() {
        return id;
    }

    @Override
    public Path root() {
        return root;
    }

    @Override
    public WorkspaceKind kind() {
        return WorkspaceKind.LOCAL;
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of();
    }
}
