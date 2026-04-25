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
package io.kairo.core.workspace;

import io.kairo.api.workspace.Workspace;
import io.kairo.api.workspace.WorkspaceKind;
import io.kairo.api.workspace.WorkspaceProvider;
import io.kairo.api.workspace.WorkspaceRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link WorkspaceProvider} backed by local filesystem directories.
 *
 * <p>Resolution rules:
 *
 * <ul>
 *   <li>{@link WorkspaceRequest#hint()} {@code == null} or blank → root is the JVM working
 *       directory ({@code Paths.get("").toAbsolutePath()}). This preserves v1.0 behaviour for
 *       callers that haven't migrated.
 *   <li>Hint is an absolute path → root is the absolute path (must exist and be a directory).
 *   <li>Hint is a relative path → resolved against the JVM working directory.
 * </ul>
 *
 * <p>The provider memoises workspaces by their normalised root path so two acquisitions for the
 * same directory return the same {@link Workspace} instance. {@link #release(String)} is a no-op
 * (local directories aren't owned by the provider).
 *
 * <p>Thread-safe.
 *
 * @since v1.1
 */
public final class LocalDirectoryWorkspaceProvider implements WorkspaceProvider {

    private static final String ID_PREFIX = "local:";

    private final Path baseDirectory;
    private final ConcurrentHashMap<String, Workspace> workspaces = new ConcurrentHashMap<>();

    /** Build a provider rooted at the JVM's current working directory. */
    public LocalDirectoryWorkspaceProvider() {
        this(Paths.get("").toAbsolutePath());
    }

    /**
     * Build a provider rooted at the given base directory. Relative hints are resolved against this
     * base instead of {@code System.getProperty("user.dir")}.
     */
    public LocalDirectoryWorkspaceProvider(Path baseDirectory) {
        this.baseDirectory =
                Objects.requireNonNull(baseDirectory, "baseDirectory").toAbsolutePath().normalize();
    }

    /** The base directory used to resolve relative hints; effectively the "default" workspace. */
    public Path baseDirectory() {
        return baseDirectory;
    }

    @Override
    public Workspace acquire(WorkspaceRequest request) {
        Objects.requireNonNull(request, "request");
        Path resolved = resolve(request.hint());
        if (!Files.isDirectory(resolved)) {
            throw new IllegalArgumentException(
                    "Workspace hint does not resolve to an existing directory: " + resolved);
        }
        String id = ID_PREFIX + resolved;
        return workspaces.computeIfAbsent(id, key -> new LocalWorkspace(key, resolved));
    }

    @Override
    public void release(String workspaceId) {
        if (workspaceId == null) {
            return;
        }
        workspaces.remove(workspaceId);
    }

    private Path resolve(String hint) {
        if (hint == null || hint.isBlank()) {
            return baseDirectory;
        }
        Path raw = Paths.get(hint);
        Path absolute = raw.isAbsolute() ? raw : baseDirectory.resolve(raw);
        return absolute.normalize();
    }

    private record LocalWorkspace(String id, Path root) implements Workspace {
        @Override
        public WorkspaceKind kind() {
            return WorkspaceKind.LOCAL;
        }

        @Override
        public Map<String, String> metadata() {
            return Map.of();
        }
    }
}
