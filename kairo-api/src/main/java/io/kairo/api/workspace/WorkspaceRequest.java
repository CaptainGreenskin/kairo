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
import io.kairo.api.tenant.TenantContext;
import javax.annotation.Nullable;

/**
 * Describes a workspace acquisition. Passed to {@link WorkspaceProvider#acquire(WorkspaceRequest)}.
 *
 * <p>Providers interpret {@link #hint()} according to their backend. The bundled {@code
 * LocalDirectoryWorkspaceProvider} treats it as a directory path (or null → cwd). A future {@code
 * RemoteGitWorkspaceProvider} would parse it as {@code "github.com/owner/repo@ref"}.
 *
 * <p>{@link #tenant()} is non-null; callers that don't multi-tenant pass {@link
 * TenantContext#SINGLE}. {@link #writable()} is advisory — providers MAY enforce read-only mounts
 * for compliance reasons but are not required to.
 *
 * @param hint provider-specific identifier (path, repo URL, etc.); {@code null} means "default"
 * @param tenant the active tenant context; never null
 * @param writable {@code true} when the caller intends to mutate files in the workspace
 * @since v1.1
 */
@Stable(value = "WorkspaceRequest record added in v1.1", since = "1.1.0")
public record WorkspaceRequest(@Nullable String hint, TenantContext tenant, boolean writable) {

    /** Compact constructor — defaults a null tenant to {@link TenantContext#SINGLE}. */
    public WorkspaceRequest {
        tenant = tenant == null ? TenantContext.SINGLE : tenant;
    }

    /** Convenience: writable request against the single-tenant sentinel. */
    public static WorkspaceRequest writable(@Nullable String hint) {
        return new WorkspaceRequest(hint, TenantContext.SINGLE, true);
    }

    /** Convenience: read-only request against the single-tenant sentinel. */
    public static WorkspaceRequest readOnly(@Nullable String hint) {
        return new WorkspaceRequest(hint, TenantContext.SINGLE, false);
    }
}
