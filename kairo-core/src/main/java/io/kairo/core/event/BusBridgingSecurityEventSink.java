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
package io.kairo.core.event;

import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.KairoEventBus;
import io.kairo.api.guardrail.SecurityEvent;
import io.kairo.api.guardrail.SecurityEventSink;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Adapter that forwards a {@link SecurityEvent} to both an underlying {@link SecurityEventSink} and
 * the {@link KairoEventBus}.
 *
 * <p>Use this to preserve the existing logging/audit contract while making every security event
 * observable through the unified event bus (OTel exporter, metrics, correlation).
 *
 * @since v0.10
 */
public class BusBridgingSecurityEventSink implements SecurityEventSink {

    private final SecurityEventSink delegate;
    private final KairoEventBus bus;

    public BusBridgingSecurityEventSink(SecurityEventSink delegate, KairoEventBus bus) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.bus = Objects.requireNonNull(bus, "bus must not be null");
    }

    @Override
    public void record(SecurityEvent event) {
        delegate.record(event);
        if (event != null) {
            bus.publish(toBusEvent(event));
        }
    }

    private static KairoEvent toBusEvent(SecurityEvent event) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("agentName", event.agentName());
        attributes.put("targetName", event.targetName());
        attributes.put("phase", event.phase().name());
        attributes.put("policyName", event.policyName());
        if (event.reason() != null && !event.reason().isBlank()) {
            attributes.put("reason", event.reason());
        }
        if (event.attributes() != null) {
            attributes.putAll(event.attributes());
        }
        return new KairoEvent(
                java.util.UUID.randomUUID().toString(),
                event.timestamp(),
                KairoEvent.DOMAIN_SECURITY,
                event.type().name(),
                event,
                attributes);
    }
}
