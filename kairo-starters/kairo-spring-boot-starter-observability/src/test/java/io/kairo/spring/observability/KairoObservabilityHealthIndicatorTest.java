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
package io.kairo.spring.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.event.KairoEvent;
import io.kairo.core.event.DefaultKairoEventBus;
import io.kairo.observability.event.KairoEventOTelExporter;
import io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

/**
 * Locks in the contract that {@link KairoObservabilityHealthIndicator} reports UP when the OTel
 * bridge is silent or healthy, flips to DOWN the instant {@link
 * KairoEventOTelExporter#exportFailedCount()} grows, and surfaces the underlying counters +
 * lastExportError as health details so operators can root-cause without grepping logs.
 */
class KairoObservabilityHealthIndicatorTest {

    @Test
    void noFailures_reportsUpWithCounters() {
        DefaultKairoEventBus bus = new DefaultKairoEventBus();
        InMemoryLogRecordExporter logExporter = InMemoryLogRecordExporter.create();
        SdkLoggerProvider loggerProvider =
                SdkLoggerProvider.builder()
                        .addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter))
                        .build();
        KairoEventOTelExporter exporter =
                new KairoEventOTelExporter(
                        bus, loggerProvider, Set.of(KairoEvent.DOMAIN_SECURITY), 1.0, null);
        exporter.start();
        bus.publish(KairoEvent.of(KairoEvent.DOMAIN_SECURITY, "GUARDRAIL_DENY", Map.of()));

        Health health = new KairoObservabilityHealthIndicator(exporter).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("exportedCount", 1L)
                .containsEntry("exportFailedCount", 0L)
                .doesNotContainKey("lastExportError");

        exporter.stop();
        loggerProvider.close();
    }

    @Test
    void anyFailure_flipsToDownAndIncludesLastError() {
        DefaultKairoEventBus bus = new DefaultKairoEventBus();
        LoggerProvider exploding = explodingLoggerProvider("boom");
        KairoEventOTelExporter exporter =
                new KairoEventOTelExporter(
                        bus, exploding, Set.of(KairoEvent.DOMAIN_SECURITY), 1.0, null);
        exporter.start();
        bus.publish(KairoEvent.of(KairoEvent.DOMAIN_SECURITY, "GUARDRAIL_DENY", Map.of()));

        Health health = new KairoObservabilityHealthIndicator(exporter).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("exportFailedCount", 1L)
                .containsEntry("exportedCount", 0L);
        assertThat((String) health.getDetails().get("lastExportError"))
                .contains("IllegalStateException")
                .contains("boom");

        exporter.stop();
    }

    static LoggerProvider explodingLoggerProvider(String message) {
        Logger explodingLogger =
                new Logger() {
                    @Override
                    public LogRecordBuilder logRecordBuilder() {
                        throw new IllegalStateException(message);
                    }
                };
        io.opentelemetry.api.logs.LoggerBuilder builder =
                new io.opentelemetry.api.logs.LoggerBuilder() {
                    @Override
                    public io.opentelemetry.api.logs.LoggerBuilder setSchemaUrl(String s) {
                        return this;
                    }

                    @Override
                    public io.opentelemetry.api.logs.LoggerBuilder setInstrumentationVersion(
                            String s) {
                        return this;
                    }

                    @Override
                    public Logger build() {
                        return explodingLogger;
                    }
                };
        return scope -> builder;
    }
}
