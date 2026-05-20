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
package io.kairo.core.a2a.remote;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.core.a2a.remote.RemoteAgentHeartbeat.EndpointStatus;
import io.kairo.core.a2a.remote.RemoteAgentHeartbeat.EventType;
import io.kairo.core.a2a.remote.RemoteAgentHeartbeat.HeartbeatEvent;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class RemoteAgentHeartbeatTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void healthyEndpointStaysAlive() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            server.enqueue(new MockResponse().setBody("{\"status\":\"ok\"}"));
        }

        try (RemoteAgentHeartbeat heartbeat =
                new RemoteAgentHeartbeat(Duration.ofMillis(100), Duration.ofSeconds(5), 3)) {
            heartbeat.registerEndpoint("ep-1", server.url("/health").toString());
            heartbeat.start();

            Thread.sleep(500);

            Map<String, EndpointStatus> snap = heartbeat.snapshot();
            assertThat(snap).containsKey("ep-1");
            assertThat(snap.get("ep-1").alive()).isTrue();
            assertThat(snap.get("ep-1").lastSuccessAt()).isNotNull();
        }
    }

    @Test
    void failedEndpointMarkedDown() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            server.enqueue(new MockResponse().setResponseCode(500));
        }

        CopyOnWriteArrayList<HeartbeatEvent> events = new CopyOnWriteArrayList<>();

        try (RemoteAgentHeartbeat heartbeat =
                new RemoteAgentHeartbeat(Duration.ofMillis(50), Duration.ofSeconds(5), 3)) {
            heartbeat.onEvent(events::add);
            heartbeat.registerEndpoint("ep-1", server.url("/health").toString());
            heartbeat.start();

            Thread.sleep(800);

            assertThat(events).anyMatch(e -> e.type() == EventType.DOWN);
            Map<String, EndpointStatus> snap = heartbeat.snapshot();
            assertThat(snap.get("ep-1").alive()).isFalse();
            assertThat(snap.get("ep-1").consecutiveFailures()).isGreaterThanOrEqualTo(3);
        }
    }

    @Test
    void recoveredEndpointFiresEvent() throws InterruptedException {
        for (int i = 0; i < 4; i++) {
            server.enqueue(new MockResponse().setResponseCode(500));
        }
        for (int i = 0; i < 5; i++) {
            server.enqueue(new MockResponse().setBody("{\"status\":\"ok\"}"));
        }

        CopyOnWriteArrayList<HeartbeatEvent> events = new CopyOnWriteArrayList<>();

        try (RemoteAgentHeartbeat heartbeat =
                new RemoteAgentHeartbeat(Duration.ofMillis(50), Duration.ofSeconds(5), 3)) {
            heartbeat.onEvent(events::add);
            heartbeat.registerEndpoint("ep-1", server.url("/health").toString());
            heartbeat.start();

            Thread.sleep(1000);

            assertThat(events).anyMatch(e -> e.type() == EventType.RECOVERED);
        }
    }

    @Test
    void unregisterRemovesFromSnapshot() {
        try (RemoteAgentHeartbeat heartbeat =
                new RemoteAgentHeartbeat(Duration.ofSeconds(30), Duration.ofSeconds(5), 3)) {
            heartbeat.registerEndpoint("ep-1", "http://localhost:9999/health");
            assertThat(heartbeat.snapshot()).containsKey("ep-1");

            heartbeat.unregisterEndpoint("ep-1");
            assertThat(heartbeat.snapshot()).doesNotContainKey("ep-1");
        }
    }

    @Test
    void snapshotEmptyWhenNoEndpoints() {
        try (RemoteAgentHeartbeat heartbeat = new RemoteAgentHeartbeat()) {
            assertThat(heartbeat.snapshot()).isEmpty();
        }
    }

    @Test
    void multipleEndpointsTrackedIndependently() throws InterruptedException {
        for (int i = 0; i < 5; i++) {
            server.enqueue(new MockResponse().setBody("{\"status\":\"ok\"}"));
        }

        try (RemoteAgentHeartbeat heartbeat =
                new RemoteAgentHeartbeat(Duration.ofMillis(100), Duration.ofSeconds(5), 3)) {
            heartbeat.registerEndpoint("ep-1", server.url("/health").toString());
            heartbeat.registerEndpoint("ep-2", "http://localhost:1/never-reachable");
            heartbeat.start();

            Thread.sleep(500);

            Map<String, EndpointStatus> snap = heartbeat.snapshot();
            assertThat(snap).containsKey("ep-1");
            assertThat(snap).containsKey("ep-2");
        }
    }
}
