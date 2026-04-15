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
package io.kairo.demo;

import io.kairo.api.memory.MemoryScope;
import io.kairo.core.memory.FileMemoryStore;
import io.kairo.core.session.SessionSerializer;
import io.kairo.core.session.SessionMetadata;
import io.kairo.core.session.SessionSnapshot;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates session persistence: FileMemoryStore and SessionSerializer.
 *
 * <p>No API key needed — exercises the persistence layer directly.
 *
 * <p>Usage:
 * <pre>
 *   mvn exec:java -pl kairo-demo -Dexec.mainClass="io.kairo.demo.SessionDemo"
 * </pre>
 */
public class SessionDemo {

    public static void main(String[] args) {
        System.out.println("=== Kairo Session Persistence Demo ===");
        System.out.println("No API key needed");
        System.out.println("======================================\n");

        Path storageDir = Path.of("/tmp/kairo-session-demo");

        // --- Part 1: FileMemoryStore ---
        System.out.println("💾 Part 1: FileMemoryStore — Scoped key-value persistence\n");

        FileMemoryStore store = new FileMemoryStore(storageDir);

        // Save data in different scopes
        store.saveRaw("user-prefs", "{\"theme\": \"dark\", \"lang\": \"zh\"}", MemoryScope.SESSION).block();
        System.out.println("  Saved: user-prefs (SESSION scope)");

        store.saveRaw("project-ctx", "{\"name\": \"kairo\", \"version\": \"0.1.0\"}", MemoryScope.PROJECT).block();
        System.out.println("  Saved: project-ctx (PROJECT scope)");

        // Load and verify
        String prefs = store.loadRaw("user-prefs", MemoryScope.SESSION).block();
        System.out.println("  Loaded user-prefs: " + prefs);

        // List keys per scope
        List<String> sessionKeys = store.listKeys(MemoryScope.SESSION).collectList().block();
        List<String> projectKeys = store.listKeys(MemoryScope.PROJECT).collectList().block();
        System.out.println("  SESSION keys: " + sessionKeys);
        System.out.println("  PROJECT keys: " + projectKeys);

        // Delete
        store.deleteRaw("user-prefs", MemoryScope.SESSION).block();
        sessionKeys = store.listKeys(MemoryScope.SESSION).collectList().block();
        System.out.println("  After delete — SESSION keys: " + sessionKeys);

        // --- Part 2: SessionSerializer ---
        System.out.println("\n📦 Part 2: SessionSerializer — Snapshot round-trip\n");

        SessionSerializer serializer = new SessionSerializer();

        SessionSnapshot snapshot = new SessionSnapshot(
                "demo-session-001",
                Instant.now(),
                3,
                List.of(
                        Map.of("role", "user", "content", "Hello!"),
                        Map.of("role", "assistant", "content", "Hi! How can I help?"),
                        Map.of("role", "user", "content", "Write a hello world")
                ),
                Map.of("iteration", 5, "tokensUsed", 1234)
        );

        String json = serializer.serialize(snapshot);
        System.out.println("  Serialized (first 150 chars): " + json.substring(0, Math.min(150, json.length())) + "...");

        SessionSnapshot restored = serializer.deserialize(json);
        System.out.println("  Deserialized: id=" + restored.sessionId()
                + " turns=" + restored.turnCount()
                + " messages=" + restored.messages().size());

        SessionMetadata meta = serializer.extractMetadata(json);
        System.out.println("  Metadata: id=" + meta.sessionId()
                + " created=" + meta.createdAt()
                + " turns=" + meta.turnCount());

        System.out.println("\n========================================");
        System.out.println("  Session Demo complete!");
        System.out.println("  Storage: " + storageDir.toAbsolutePath());
        System.out.println("========================================");
    }
}
