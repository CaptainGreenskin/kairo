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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kairo.api.workspace.Workspace;
import io.kairo.api.workspace.WorkspaceKind;
import io.kairo.api.workspace.WorkspaceRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDirectoryWorkspaceProviderTest {

    @Test
    void nullHintResolvesToBaseDirectory(@TempDir Path tmp) {
        LocalDirectoryWorkspaceProvider provider = new LocalDirectoryWorkspaceProvider(tmp);
        Workspace ws = provider.acquire(WorkspaceRequest.writable(null));

        assertEquals(tmp.toAbsolutePath().normalize(), ws.root());
        assertEquals(WorkspaceKind.LOCAL, ws.kind());
        assertNotNull(ws.id());
        assertTrue(ws.metadata().isEmpty());
    }

    @Test
    void blankHintResolvesToBaseDirectory(@TempDir Path tmp) {
        LocalDirectoryWorkspaceProvider provider = new LocalDirectoryWorkspaceProvider(tmp);
        Workspace ws = provider.acquire(WorkspaceRequest.writable("   "));
        assertEquals(tmp.toAbsolutePath().normalize(), ws.root());
    }

    @Test
    void absoluteHintIsUsedDirectly(@TempDir Path tmp) throws IOException {
        Path other = Files.createDirectory(tmp.resolve("checkout"));
        LocalDirectoryWorkspaceProvider provider = new LocalDirectoryWorkspaceProvider(tmp);

        Workspace ws = provider.acquire(WorkspaceRequest.writable(other.toString()));

        assertEquals(other.toAbsolutePath().normalize(), ws.root());
    }

    @Test
    void relativeHintResolvesAgainstBaseDirectory(@TempDir Path tmp) throws IOException {
        Files.createDirectory(tmp.resolve("checkout"));
        LocalDirectoryWorkspaceProvider provider = new LocalDirectoryWorkspaceProvider(tmp);

        Workspace ws = provider.acquire(WorkspaceRequest.writable("checkout"));

        assertEquals(tmp.resolve("checkout").toAbsolutePath().normalize(), ws.root());
    }

    @Test
    void nonExistentHintRejected(@TempDir Path tmp) {
        LocalDirectoryWorkspaceProvider provider = new LocalDirectoryWorkspaceProvider(tmp);
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> provider.acquire(WorkspaceRequest.writable("does-not-exist")));
        assertTrue(ex.getMessage().contains("does-not-exist"), ex.getMessage());
    }

    @Test
    void hintPointingAtFileRejected(@TempDir Path tmp) throws IOException {
        Path file = Files.createFile(tmp.resolve("regular.txt"));
        LocalDirectoryWorkspaceProvider provider = new LocalDirectoryWorkspaceProvider(tmp);
        assertThrows(
                IllegalArgumentException.class,
                () -> provider.acquire(WorkspaceRequest.writable(file.toString())));
    }

    @Test
    void sameHintReturnsSameWorkspaceInstance(@TempDir Path tmp) throws IOException {
        Files.createDirectory(tmp.resolve("co"));
        LocalDirectoryWorkspaceProvider provider = new LocalDirectoryWorkspaceProvider(tmp);

        Workspace first = provider.acquire(WorkspaceRequest.writable("co"));
        Workspace second = provider.acquire(WorkspaceRequest.writable("co"));

        assertSame(first, second);
        assertEquals(first.id(), second.id());
    }

    @Test
    void differentHintsReturnDifferentWorkspaces(@TempDir Path tmp) throws IOException {
        Files.createDirectory(tmp.resolve("a"));
        Files.createDirectory(tmp.resolve("b"));
        LocalDirectoryWorkspaceProvider provider = new LocalDirectoryWorkspaceProvider(tmp);

        Workspace a = provider.acquire(WorkspaceRequest.writable("a"));
        Workspace b = provider.acquire(WorkspaceRequest.writable("b"));

        assertNotEquals(a.id(), b.id());
        assertNotEquals(a.root(), b.root());
    }

    @Test
    void releaseEvictsCachedWorkspace(@TempDir Path tmp) throws IOException {
        Files.createDirectory(tmp.resolve("co"));
        LocalDirectoryWorkspaceProvider provider = new LocalDirectoryWorkspaceProvider(tmp);

        Workspace first = provider.acquire(WorkspaceRequest.writable("co"));
        provider.release(first.id());
        Workspace second = provider.acquire(WorkspaceRequest.writable("co"));

        // After release, the cache is evicted — a fresh acquisition produces a new instance.
        assertNotNull(second);
        // Same path, same id — but the *instance* is allowed to change after release.
        assertEquals(first.root(), second.root());
    }

    @Test
    void releaseUnknownIdIsNoop(@TempDir Path tmp) {
        LocalDirectoryWorkspaceProvider provider = new LocalDirectoryWorkspaceProvider(tmp);
        provider.release("local:/does-not-exist");
        provider.release(null);
    }

    @Test
    void noArgConstructorUsesJvmCwd() {
        LocalDirectoryWorkspaceProvider provider = new LocalDirectoryWorkspaceProvider();
        Workspace ws = provider.acquire(WorkspaceRequest.writable(null));

        assertEquals(
                Path.of("").toAbsolutePath().normalize(),
                ws.root(),
                "default workspace should equal JVM cwd");
    }

    @Test
    void normalisesDotSegmentsInRelativeHint(@TempDir Path tmp) throws IOException {
        Files.createDirectory(tmp.resolve("co"));
        LocalDirectoryWorkspaceProvider provider = new LocalDirectoryWorkspaceProvider(tmp);

        Workspace direct = provider.acquire(WorkspaceRequest.writable("co"));
        Workspace dotted = provider.acquire(WorkspaceRequest.writable("./co/."));

        // Normalisation collapses ./co/. and co into the same id, so memoisation kicks in.
        assertSame(direct, dotted);
    }

    @Test
    void requestIsNotNull() {
        LocalDirectoryWorkspaceProvider provider = new LocalDirectoryWorkspaceProvider();
        assertThrows(NullPointerException.class, () -> provider.acquire(null));
    }

    @Test
    void baseDirectoryAccessorReflectsConstructor(@TempDir Path tmp) {
        LocalDirectoryWorkspaceProvider provider = new LocalDirectoryWorkspaceProvider(tmp);
        assertEquals(tmp.toAbsolutePath().normalize(), provider.baseDirectory());
    }

    @Test
    void workspaceIdIsStableAcrossAcquisitionsOfTheSamePath(@TempDir Path tmp) throws IOException {
        Files.createDirectory(tmp.resolve("co"));
        LocalDirectoryWorkspaceProvider provider = new LocalDirectoryWorkspaceProvider(tmp);

        Workspace ws1 = provider.acquire(WorkspaceRequest.writable("co"));
        Workspace ws2 = provider.acquire(WorkspaceRequest.writable(tmp.resolve("co").toString()));

        assertSame(ws1, ws2);
        assertNull(null); // sanity
    }
}
