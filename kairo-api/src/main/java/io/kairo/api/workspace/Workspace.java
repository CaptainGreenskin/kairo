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

import io.kairo.api.Stable;
import java.nio.file.Path;
import java.util.Map;

/**
 * A handle to a working directory available to tools and agents.
 *
 * <p>Conceptually this is a cross-cutting context: file tools resolve relative paths against {@link
 * #root()}, and the {@code ExecutionSandbox} mounts {@link #root()} as the sandbox's {@code
 * workspaceRoot}. Implementations are responsible for keeping {@link #root()} valid for the
 * lifetime they advertise; ephemeral implementations should be released through their owning {@link
 * WorkspaceProvider} when no longer needed.
 *
 * <p>Implementations MUST be safe for concurrent reads via {@link #root()} and {@link #metadata()}.
 * Mutations to underlying files are not synchronised by this SPI — callers serialise as needed.
 *
 * @since v1.1
 */
@Stable(value = "Workspace SPI added in v1.1", since = "1.1.0")
public interface Workspace {

    /**
     * Stable identifier for this workspace within its provider. Two workspaces obtained for the
     * same {@link WorkspaceRequest} MAY share the same id only when they are the same physical
     * checkout.
     */
    String id();

    /**
     * The root directory of the workspace. For {@link WorkspaceKind#LOCAL} this is the on-disk
     * path; for {@link WorkspaceKind#REMOTE_GIT} this is the local checkout; for {@link
     * WorkspaceKind#EPHEMERAL} this is the mount point.
     */
    Path root();

    /** The backend taxonomy for this workspace. */
    WorkspaceKind kind();

    /**
     * Provider-specific metadata. Implementations SHOULD use stable string keys; common keys
     * include {@code git.remote}, {@code git.branch}, {@code git.commit}.
     */
    Map<String, String> metadata();

    /**
     * Returns a workspace rooted at the JVM's current working directory.
     *
     * <p>This is the default workspace used by {@link io.kairo.api.tool.ToolContext} when no
     * explicit workspace is bound. It exists so kairo-api callers can produce a valid {@link
     * Workspace} without depending on kairo-core; production code with multiple workspaces should
     * use {@code LocalDirectoryWorkspaceProvider} instead.
     */
    static Workspace cwd() {
        return CwdWorkspace.INSTANCE;
    }
}
