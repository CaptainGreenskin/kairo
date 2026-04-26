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
package io.kairo.spring.eventstream;

import io.kairo.api.event.stream.BackpressurePolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binding for {@code kairo.event-stream.*} properties consumed by {@link
 * EventStreamAutoConfiguration}.
 *
 * <p>Opt-in via {@code kairo.event-stream.enabled=true}. Transports (SSE, WebSocket) individually
 * toggle via {@code kairo.event-stream.sse.enabled} / {@code kairo.event-stream.ws.enabled}.
 *
 * @since v0.9
 */
@ConfigurationProperties(prefix = "kairo.event-stream")
public class EventStreamProperties {

    /** Master switch for event-stream auto-configuration. */
    private boolean enabled = false;

    /** Default per-subscription buffer capacity. */
    private int defaultBufferCapacity = 1024;

    /** Default backpressure policy applied when the client does not override it. */
    private BackpressurePolicy defaultPolicy = BackpressurePolicy.BUFFER_DROP_OLDEST;

    private final Sse sse = new Sse();
    private final Ws ws = new Ws();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDefaultBufferCapacity() {
        return defaultBufferCapacity;
    }

    public void setDefaultBufferCapacity(int defaultBufferCapacity) {
        this.defaultBufferCapacity = defaultBufferCapacity;
    }

    public BackpressurePolicy getDefaultPolicy() {
        return defaultPolicy;
    }

    public void setDefaultPolicy(BackpressurePolicy defaultPolicy) {
        this.defaultPolicy = defaultPolicy;
    }

    public Sse getSse() {
        return sse;
    }

    public Ws getWs() {
        return ws;
    }

    /** SSE-specific transport configuration. */
    public static class Sse {
        /** Enable the HTTP SSE endpoint. */
        private boolean enabled = true;

        /** Path at which {@link KairoEventStreamSseController} is mapped. */
        private String path = "/kairo/event-stream/sse";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    /** WebSocket-specific transport configuration. */
    public static class Ws {
        /** Enable the reactive WebSocket endpoint. */
        private boolean enabled = true;

        /** Path at which the WebSocket handler is mapped. */
        private String path = "/kairo/event-stream/ws";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }
}
