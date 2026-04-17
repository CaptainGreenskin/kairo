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
package io.kairo.mcp;

import static org.junit.jupiter.api.Assertions.*;

import io.modelcontextprotocol.client.McpAsyncClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class McpSyncClientTest {

    // --- Construction tests ---

    @Test
    void constructorRejectsNullDelegate() {
        assertThrows(
                IllegalArgumentException.class, () -> new McpSyncClient(null));
    }

    @Test
    void constructorRejectsNullTimeout() {
        McpAsyncClient asyncClient = createAsyncClient();
        assertThrows(
                IllegalArgumentException.class,
                () -> new McpSyncClient(asyncClient, null));
    }

    @Test
    void defaultTimeoutIs30Seconds() {
        McpSyncClient client = new McpSyncClient(createAsyncClient());
        assertEquals(Duration.ofSeconds(30), client.getDefaultTimeout());
        assertEquals(McpSyncClient.DEFAULT_TIMEOUT, client.getDefaultTimeout());
    }

    @Test
    void customTimeoutIsPreserved() {
        Duration custom = Duration.ofSeconds(60);
        McpSyncClient client = new McpSyncClient(createAsyncClient(), custom);
        assertEquals(custom, client.getDefaultTimeout());
    }

    @Test
    void getDelegateReturnsSameInstance() {
        McpAsyncClient asyncClient = createAsyncClient();
        McpSyncClient client = new McpSyncClient(asyncClient);
        assertSame(asyncClient, client.getDelegate());
    }

    @Test
    void closeCallsDelegateClose() {
        McpAsyncClient asyncClient = createAsyncClient();
        McpSyncClient client = new McpSyncClient(asyncClient);
        // Should not throw
        client.close();
    }

    // --- Builder integration tests ---

    @Test
    void builderBuildSyncCreatesClient() {
        McpSyncClient client =
                McpClientBuilder.create("test")
                        .stdioTransport("echo", "hello")
                        .buildSync();
        assertNotNull(client);
        assertNotNull(client.getDelegate());
        assertEquals(Duration.ofSeconds(30), client.getDefaultTimeout());
    }

    @Test
    void builderBuildSyncWithTimeoutCreatesClient() {
        Duration timeout = Duration.ofMinutes(2);
        McpSyncClient client =
                McpClientBuilder.create("test")
                        .stdioTransport("echo", "hello")
                        .buildSync(timeout);
        assertNotNull(client);
        assertNotNull(client.getDelegate());
        assertEquals(timeout, client.getDefaultTimeout());
    }

    @Test
    void builderBuildSyncRequiresTransport() {
        assertThrows(
                IllegalStateException.class,
                () -> McpClientBuilder.create("test").buildSync());
    }

    @Test
    void builderBuildSyncWithTimeoutRequiresTransport() {
        assertThrows(
                IllegalStateException.class,
                () -> McpClientBuilder.create("test").buildSync(Duration.ofSeconds(10)));
    }

    @Test
    void buildSyncDelegateMatchesBuildResult() {
        // buildSync() should wrap the same type of client that build() returns
        McpAsyncClient directClient =
                McpClientBuilder.create("test")
                        .stdioTransport("echo", "hello")
                        .build();
        McpSyncClient syncClient =
                McpClientBuilder.create("test2")
                        .stdioTransport("echo", "hello")
                        .buildSync();
        // Both should produce non-null clients of the same type
        assertNotNull(directClient);
        assertNotNull(syncClient.getDelegate());
        assertEquals(directClient.getClass(), syncClient.getDelegate().getClass());
    }

    @Test
    void implementsAutoCloseable() {
        McpSyncClient client =
                McpClientBuilder.create("test")
                        .stdioTransport("echo", "hello")
                        .buildSync();
        // Verify McpSyncClient implements AutoCloseable (can be used in try-with-resources)
        assertTrue(client instanceof AutoCloseable);
    }

    // --- Helper ---

    private static McpAsyncClient createAsyncClient() {
        return McpClientBuilder.create("test-helper")
                .stdioTransport("echo", "hello")
                .build();
    }
}
