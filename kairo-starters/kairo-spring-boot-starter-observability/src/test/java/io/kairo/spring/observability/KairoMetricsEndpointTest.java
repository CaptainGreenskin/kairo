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

import io.kairo.api.event.KairoEventBus;
import io.kairo.core.event.DefaultKairoEventBus;
import io.kairo.observability.event.KairoEventOTelExporter;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class KairoMetricsEndpointTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class));

    @Test
    void withoutOpenTelemetry_noEndpointBean() {
        runner.withUserConfiguration(BusOnly.class)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(KairoMetricsEndpoint.class));
    }

    @Test
    void withOpenTelemetry_wiresEndpoint() {
        runner.withPropertyValues("kairo.observability.event-otel.enabled=true")
                .withUserConfiguration(FullInfra.class)
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(KairoMetricsEndpoint.class);
                            KairoMetricsEndpoint endpoint = ctx.getBean(KairoMetricsEndpoint.class);
                            Map<String, Object> metrics = endpoint.metrics();

                            assertThat(metrics).containsKey("tracerName");
                            assertThat(metrics).containsKey("isEnabled");
                            assertThat(metrics).containsKey("version");
                            assertThat(metrics).containsKey("eventExporter");

                            assertThat((String) metrics.get("tracerName")).isNotBlank();
                            assertThat(metrics.get("isEnabled")).isEqualTo(true);
                            assertThat(metrics.get("version")).isEqualTo("1.0.0-SNAPSHOT");

                            @SuppressWarnings("unchecked")
                            Map<String, Object> exporter =
                                    (Map<String, Object>) metrics.get("eventExporter");
                            assertThat(exporter)
                                    .containsEntry("present", true)
                                    .containsKeys(
                                            "exportedCount",
                                            "droppedByDomainCount",
                                            "droppedBySamplingCount",
                                            "exportFailedCount");
                        });
    }

    @Test
    void withoutExporter_endpointReportsExporterAbsent() {
        // enabled=true triggers the auto-config; OpenTelemetryOnly provides only OpenTelemetry
        // (no KairoEventBus / LoggerProvider), so the exporter bean is NOT created — endpoint
        // should still wire and report present=false for the exporter section.
        runner.withPropertyValues("kairo.observability.event-otel.enabled=true")
                .withUserConfiguration(OpenTelemetryOnly.class)
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(KairoMetricsEndpoint.class);
                            assertThat(ctx).doesNotHaveBean(KairoEventOTelExporter.class);
                            Map<String, Object> metrics =
                                    ctx.getBean(KairoMetricsEndpoint.class).metrics();
                            @SuppressWarnings("unchecked")
                            Map<String, Object> exporter =
                                    (Map<String, Object>) metrics.get("eventExporter");
                            assertThat(exporter).containsEntry("present", false);
                        });
    }

    @Test
    void customEndpointOverridesDefault() {
        runner.withPropertyValues("kairo.observability.event-otel.enabled=true")
                .withUserConfiguration(FullInfra.class, CustomEndpoint.class)
                .run(
                        ctx -> {
                            assertThat(ctx)
                                    .getBean(KairoMetricsEndpoint.class)
                                    .isInstanceOf(CustomKairoMetricsEndpoint.class);
                        });
    }

    @Configuration
    static class BusOnly {
        @Bean
        KairoEventBus bus() {
            return new DefaultKairoEventBus();
        }
    }

    @Configuration
    static class OpenTelemetryOnly {
        @Bean
        OpenTelemetry openTelemetry() {
            return OpenTelemetrySdk.builder()
                    .setTracerProvider(SdkTracerProvider.builder().build())
                    .build();
        }
    }

    @Configuration
    static class FullInfra {
        @Bean
        KairoEventBus bus() {
            return new DefaultKairoEventBus();
        }

        @Bean
        InMemoryLogRecordExporter inMemoryLogRecordExporter() {
            return InMemoryLogRecordExporter.create();
        }

        @Bean(destroyMethod = "close")
        LoggerProvider loggerProvider(InMemoryLogRecordExporter logExporter) {
            return SdkLoggerProvider.builder()
                    .addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter))
                    .build();
        }

        @Bean
        OpenTelemetry openTelemetry() {
            return OpenTelemetrySdk.builder()
                    .setTracerProvider(SdkTracerProvider.builder().build())
                    .setLoggerProvider(
                            SdkLoggerProvider.builder()
                                    .addLogRecordProcessor(
                                            SimpleLogRecordProcessor.create(
                                                    InMemoryLogRecordExporter.create()))
                                    .build())
                    .build();
        }
    }

    static class CustomKairoMetricsEndpoint extends KairoMetricsEndpoint {
        CustomKairoMetricsEndpoint() {
            super(io.opentelemetry.api.GlobalOpenTelemetry.getTracer("custom"));
        }
    }

    @Configuration
    static class CustomEndpoint {
        @Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
        KairoMetricsEndpoint customKairoMetricsEndpoint() {
            return new CustomKairoMetricsEndpoint();
        }
    }
}
