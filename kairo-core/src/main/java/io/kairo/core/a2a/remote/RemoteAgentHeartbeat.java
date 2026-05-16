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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Periodic heartbeat monitor for remote A2A agent endpoints.
 *
 * <p>Pings the health endpoint of each registered remote agent at a configurable interval.
 * Listeners are notified when an agent becomes unreachable or recovers.
 */
public final class RemoteAgentHeartbeat implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RemoteAgentHeartbeat.class);
    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(30);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final int DEFAULT_FAILURE_THRESHOLD = 3;

    private final Duration interval;
    private final Duration pingTimeout;
    private final int failureThreshold;
    private final Map<String, EndpointState> endpoints = new ConcurrentHashMap<>();
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private @Nullable Consumer<HeartbeatEvent> eventListener;

    public RemoteAgentHeartbeat() {
        this(DEFAULT_INTERVAL, DEFAULT_TIMEOUT, DEFAULT_FAILURE_THRESHOLD);
    }

    public RemoteAgentHeartbeat(Duration interval, Duration pingTimeout, int failureThreshold) {
        this.interval = Objects.requireNonNull(interval, "interval must not be null");
        this.pingTimeout = Objects.requireNonNull(pingTimeout, "pingTimeout must not be null");
        this.failureThreshold = failureThreshold;
        this.httpClient = HttpClient.newBuilder().connectTimeout(pingTimeout).build();
        this.scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "a2a-heartbeat");
                            t.setDaemon(true);
                            return t;
                        });
    }

    public void onEvent(Consumer<HeartbeatEvent> listener) {
        this.eventListener = listener;
    }

    public void registerEndpoint(String endpointId, String healthUrl) {
        endpoints.put(endpointId, new EndpointState(endpointId, healthUrl));
        log.debug("Registered heartbeat for endpoint '{}' at {}", endpointId, healthUrl);
    }

    public void unregisterEndpoint(String endpointId) {
        endpoints.remove(endpointId);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler.scheduleAtFixedRate(
                    this::pingAll, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
            log.info(
                    "Heartbeat monitor started (interval={}s, threshold={})",
                    interval.toSeconds(),
                    failureThreshold);
        }
    }

    @Override
    public void close() {
        running.set(false);
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }

    public Map<String, EndpointStatus> snapshot() {
        Map<String, EndpointStatus> result = new ConcurrentHashMap<>();
        for (Map.Entry<String, EndpointState> entry : endpoints.entrySet()) {
            EndpointState state = entry.getValue();
            result.put(
                    entry.getKey(),
                    new EndpointStatus(
                            state.endpointId,
                            state.healthUrl,
                            state.alive,
                            state.consecutiveFailures,
                            state.lastSuccessAt,
                            state.lastFailureAt));
        }
        return result;
    }

    private void pingAll() {
        for (EndpointState state : endpoints.values()) {
            try {
                pingOne(state);
            } catch (Exception e) {
                log.warn("Heartbeat check failed for '{}': {}", state.endpointId, e.getMessage());
            }
        }
    }

    private void pingOne(EndpointState state) {
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(state.healthUrl))
                        .timeout(pingTimeout)
                        .GET()
                        .build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                boolean wasDown = !state.alive;
                state.alive = true;
                state.consecutiveFailures = 0;
                state.lastSuccessAt = Instant.now();

                if (wasDown) {
                    fireEvent(new HeartbeatEvent(state.endpointId, EventType.RECOVERED, null));
                }
            } else {
                recordFailure(state, "HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            recordFailure(state, e.getMessage());
        }
    }

    private void recordFailure(EndpointState state, String reason) {
        state.consecutiveFailures++;
        state.lastFailureAt = Instant.now();

        if (state.alive && state.consecutiveFailures >= failureThreshold) {
            state.alive = false;
            log.warn(
                    "Endpoint '{}' marked DOWN after {} consecutive failures: {}",
                    state.endpointId,
                    state.consecutiveFailures,
                    reason);
            fireEvent(new HeartbeatEvent(state.endpointId, EventType.DOWN, reason));
        }
    }

    private void fireEvent(HeartbeatEvent event) {
        Consumer<HeartbeatEvent> listener = this.eventListener;
        if (listener != null) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.warn("Heartbeat event listener threw: {}", e.getMessage());
            }
        }
    }

    public enum EventType {
        DOWN,
        RECOVERED
    }

    public record HeartbeatEvent(String endpointId, EventType type, @Nullable String reason) {}

    public record EndpointStatus(
            String endpointId,
            String healthUrl,
            boolean alive,
            int consecutiveFailures,
            @Nullable Instant lastSuccessAt,
            @Nullable Instant lastFailureAt) {}

    private static final class EndpointState {
        final String endpointId;
        final String healthUrl;
        volatile boolean alive = true;
        volatile int consecutiveFailures = 0;
        volatile @Nullable Instant lastSuccessAt;
        volatile @Nullable Instant lastFailureAt;

        EndpointState(String endpointId, String healthUrl) {
            this.endpointId = endpointId;
            this.healthUrl = healthUrl;
        }
    }
}
