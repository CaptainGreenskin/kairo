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
package io.kairo.examples.observability;

import io.kairo.api.event.KairoEvent;
import io.kairo.core.event.DefaultKairoEventBus;
import io.kairo.observability.event.KairoEventOTelExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * End-to-end observability demo: wire {@code DefaultKairoEventBus} → {@code KairoEventOTelExporter}
 * → OpenTelemetry {@code LoggerProvider}, publish a few events, and print the resulting OTel log
 * records to stdout.
 *
 * <p>The demo uses an inline {@link LogRecordExporter} that formats each record as a single line so
 * you can see the flattened {@code kairo.<domain>.*} attributes without wiring any external
 * backend. In production, swap the stdout exporter for OTLP (Grafana Tempo/Loki, Langfuse, any OTel
 * Collector) by replacing the processor registration.
 *
 * <p>Security events are always-on (regardless of sampling); execution / team events honour the
 * {@code samplingRatio} argument. The attribute redaction pattern matches flattened keys (e.g.
 * {@code kairo.security.secret}); matches are replaced with {@code <redacted>} before emission.
 *
 * <p>Run: {@code mvn -pl kairo-examples exec:java
 * -Dexec.mainClass=io.kairo.examples.observability.ObservabilityExample}
 *
 * @since v1.0.0-RC2
 */
public final class ObservabilityExample {

    private ObservabilityExample() {}

    public static void main(String[] args) {
        DefaultKairoEventBus bus = new DefaultKairoEventBus();
        SdkLoggerProvider loggerProvider =
                SdkLoggerProvider.builder()
                        .addLogRecordProcessor(
                                SimpleLogRecordProcessor.create(new StdoutExporter()))
                        .build();

        KairoEventOTelExporter exporter =
                new KairoEventOTelExporter(
                        bus,
                        loggerProvider,
                        Set.of(
                                KairoEvent.DOMAIN_SECURITY,
                                KairoEvent.DOMAIN_EXECUTION,
                                KairoEvent.DOMAIN_TEAM),
                        1.0,
                        null);
        exporter.start();

        bus.publish(
                new KairoEvent(
                        "sec-1",
                        Instant.now(),
                        KairoEvent.DOMAIN_SECURITY,
                        "GUARDRAIL_DENY",
                        null,
                        Map.of("rule", "no-secrets", "decision", "deny")));
        bus.publish(
                new KairoEvent(
                        "exec-1",
                        Instant.now(),
                        KairoEvent.DOMAIN_EXECUTION,
                        "TOOL_CALL_START",
                        null,
                        Map.of("tool", "search", "latencyMs", 42)));
        bus.publish(
                new KairoEvent(
                        "team-1",
                        Instant.now(),
                        KairoEvent.DOMAIN_TEAM,
                        "PLAN_CREATED",
                        null,
                        Map.of("steps", 3, "planner", "DefaultPlanner")));

        System.out.printf(
                "%nExported=%d  droppedBySampling=%d  droppedByDomain=%d%n",
                exporter.exportedCount(),
                exporter.droppedBySamplingCount(),
                exporter.droppedByDomainCount());

        exporter.stop();
        loggerProvider.close();
    }

    private static final class StdoutExporter implements LogRecordExporter {
        @Override
        public CompletableResultCode export(Collection<LogRecordData> logs) {
            for (LogRecordData r : logs) {
                System.out.printf(
                        "[%s] %-9s %s  %s%n",
                        Instant.ofEpochSecond(0L, r.getTimestampEpochNanos()),
                        r.getSeverity(),
                        r.getBody().asString(),
                        r.getAttributes());
            }
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }
}
