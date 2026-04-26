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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kairo.api.tenant.TenantContext;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkspaceRequestTest {

    @Test
    void nullTenantCoercesToSingleSentinel() {
        WorkspaceRequest req = new WorkspaceRequest("/tmp/ws", null, true);
        assertSame(TenantContext.SINGLE, req.tenant());
    }

    @Test
    void carriesAllFields() {
        TenantContext acme = new TenantContext("acme", "alice", Map.of());
        WorkspaceRequest req = new WorkspaceRequest("github.com/foo/bar@main", acme, false);

        assertEquals("github.com/foo/bar@main", req.hint());
        assertSame(acme, req.tenant());
        assertFalse(req.writable());
    }

    @Test
    void writableShorthandReturnsWritableSingleTenantRequest() {
        WorkspaceRequest req = WorkspaceRequest.writable("/tmp/ws");
        assertEquals("/tmp/ws", req.hint());
        assertSame(TenantContext.SINGLE, req.tenant());
        assertTrue(req.writable());
    }

    @Test
    void readOnlyShorthandReturnsReadOnlySingleTenantRequest() {
        WorkspaceRequest req = WorkspaceRequest.readOnly("/tmp/ws");
        assertEquals("/tmp/ws", req.hint());
        assertSame(TenantContext.SINGLE, req.tenant());
        assertFalse(req.writable());
    }

    @Test
    void hintMayBeNull() {
        WorkspaceRequest req = WorkspaceRequest.writable(null);
        assertNull(req.hint());
    }
}
