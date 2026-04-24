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
package io.kairo.observability.event;

import io.kairo.api.Experimental;
import io.kairo.api.event.KairoEvent;
import io.kairo.api.event.KairoEventBus;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.logs.Severity;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

/**
 * Bridges {@link KairoEventBus} envelopes to OpenTelemetry {@code LogRecord} emission.
 *
 * <p>Per ADR-022 (and the v0.9 remaining-P0 scoping doc), security events are always-on while
 * execution/team events honour a configurable {@code samplingRatio}. Attribute names are flattened
 * under {@code kairo.<domain>.*} and user-supplied attribute values are redacted through a
 * configurable pattern list before being attached to the log record.
 *
 * <p>The exporter is inert until {@link #start()} is called and releases its subscription via
 * {@link #stop()}. It MUST NOT throw across the bus boundary — any conversion error is logged
 * locally but never propagates back into Kairo's publish path.
 *
 * @since v0.9 (Experimental)
 */
@Experimental("KairoEventOTelExporter — contract may change in v0.10")
public final class KairoEventOTelExporter {

    private static final org.slf4j.Logger LOG =
            LoggerFactory.getLogger(KairoEventOTelExporter.class);
    private static final String INSTRUMENTATION_SCOPE = "io.kairo.observability.event";
    private static final String REDACTED = "<redacted>";

    private final KairoEventBus bus;
    private final Logger logger;
    private final Set<String> includeDomains;
    private final double samplingRatio;
    private final Pattern attributeRedactionPattern;

    private final AtomicLong exportedCount = new AtomicLong();
    private final AtomicLong droppedBySamplingCount = new AtomicLong();
    private final AtomicLong droppedByDomainCount = new AtomicLong();

    private volatile Disposable subscription;

    public KairoEventOTelExporter(
            KairoEventBus bus,
            LoggerProvider loggerProvider,
            Set<String> includeDomains,
            double samplingRatio,
            Pattern attributeRedactionPattern) {
        this.bus = Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(loggerProvider, "loggerProvider");
        this.logger = loggerProvider.get(INSTRUMENTATION_SCOPE);
        this.includeDomains = Set.copyOf(Objects.requireNonNull(includeDomains, "includeDomains"));
        if (samplingRatio < 0.0 || samplingRatio > 1.0) {
            throw new IllegalArgumentException(
                    "samplingRatio must be between 0.0 and 1.0 (got " + samplingRatio + ")");
        }
        this.samplingRatio = samplingRatio;
        this.attributeRedactionPattern = attributeRedactionPattern;
    }

    /** Subscribe to the bus. Idempotent: calling twice before {@link #stop()} is a no-op. */
    public synchronized void start() {
        if (subscription != null && !subscription.isDisposed()) {
            return;
        }
        subscription =
                bus.subscribe()
                        .subscribe(
                                this::exportSafely,
                                err ->
                                        LOG.warn(
                                                "KairoEventOTelExporter subscription terminated",
                                                err));
    }

    /** Release the subscription. Safe to call multiple times. */
    public synchronized void stop() {
        Disposable current = subscription;
        if (current != null) {
            current.dispose();
            subscription = null;
        }
    }

    /**
     * Number of envelopes successfully exported since {@link #start()}. Exposed for tests and
     * lightweight observability; callers should prefer the OTel SDK's own metrics for production.
     */
    public long exportedCount() {
        return exportedCount.get();
    }

    /** Number of envelopes dropped because the domain is not in {@code includeDomains}. */
    public long droppedByDomainCount() {
        return droppedByDomainCount.get();
    }

    /**
     * Number of envelopes dropped by sampling. Security envelopes are always-on and never counted
     * here.
     */
    public long droppedBySamplingCount() {
        return droppedBySamplingCount.get();
    }

    private void exportSafely(KairoEvent event) {
        try {
            export(event);
        } catch (RuntimeException ex) {
            LOG.warn("Failed to export KairoEvent {}", event.eventId(), ex);
        }
    }

    private void export(KairoEvent event) {
        if (!includeDomains.contains(event.domain())) {
            droppedByDomainCount.incrementAndGet();
            return;
        }
        if (!KairoEvent.DOMAIN_SECURITY.equals(event.domain()) && !sampleIn()) {
            droppedBySamplingCount.incrementAndGet();
            return;
        }

        AttributesBuilder attrs = Attributes.builder();
        attrs.put("kairo.event.id", event.eventId());
        attrs.put("kairo.domain", event.domain());
        attrs.put("kairo.event.type", event.eventType());

        String prefix = "kairo." + event.domain() + ".";
        for (var entry : event.attributes().entrySet()) {
            Object value = entry.getValue();
            String flatKey = prefix + entry.getKey();
            attrs.put(AttributeKey.stringKey(flatKey), redactIfNeeded(flatKey, value));
        }

        LogRecordBuilder builder =
                logger.logRecordBuilder()
                        .setSeverity(severityFor(event.domain()))
                        .setSeverityText(event.domain())
                        .setTimestamp(event.timestamp())
                        .setBody(event.eventType())
                        .setAllAttributes(attrs.build());
        builder.emit();
        exportedCount.incrementAndGet();
    }

    private boolean sampleIn() {
        if (samplingRatio >= 1.0) {
            return true;
        }
        if (samplingRatio <= 0.0) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < samplingRatio;
    }

    private String redactIfNeeded(String key, Object value) {
        String raw = String.valueOf(value);
        if (attributeRedactionPattern == null) {
            return raw;
        }
        return attributeRedactionPattern.matcher(key).matches() ? REDACTED : raw;
    }

    private static Severity severityFor(String domain) {
        return switch (domain) {
            case KairoEvent.DOMAIN_SECURITY -> Severity.WARN;
            case KairoEvent.DOMAIN_EVOLUTION -> Severity.INFO;
            default -> Severity.INFO;
        };
    }
}
