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
import io.kairo.api.event.KairoEventBus;
import io.kairo.core.event.DefaultKairoEventBus;
import io.kairo.observability.event.KairoEventOTelExporter;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ObservabilityAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ObservabilityAutoConfiguration.class));

    @Test
    void defaultOff_noExporterBean() {
        runner.withUserConfiguration(TestInfra.class)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(KairoEventOTelExporter.class));
    }

    @Test
    void enabledButNoLoggerProvider_noExporter() {
        runner.withPropertyValues("kairo.observability.event-otel.enabled=true")
                .withUserConfiguration(BusOnly.class)
                .run(ctx -> assertThat(ctx).doesNotHaveBean(KairoEventOTelExporter.class));
    }

    @Test
    void enabledWithInfra_wiresAndStartsExporter() {
        runner.withPropertyValues("kairo.observability.event-otel.enabled=true")
                .withUserConfiguration(TestInfra.class)
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(KairoEventOTelExporter.class);
                            KairoEventBus bus = ctx.getBean(KairoEventBus.class);
                            InMemoryLogRecordExporter logs =
                                    ctx.getBean(InMemoryLogRecordExporter.class);

                            bus.publish(
                                    KairoEvent.of(
                                            KairoEvent.DOMAIN_SECURITY,
                                            "GUARDRAIL_DENY",
                                            Map.of()));
                            bus.publish(
                                    KairoEvent.of(
                                            KairoEvent.DOMAIN_EXECUTION, "MODEL_TURN", Map.of()));

                            // Default include-domains = [security] only.
                            List<LogRecordData> records = logs.getFinishedLogRecordItems();
                            assertThat(records).hasSize(1);
                            assertThat(
                                            records.get(0)
                                                    .getAttributes()
                                                    .get(
                                                            io.opentelemetry.api.common.AttributeKey
                                                                    .stringKey("kairo.domain")))
                                    .isEqualTo(KairoEvent.DOMAIN_SECURITY);
                        });
    }

    @Test
    void customIncludeDomainsAndRedaction_applied() {
        runner.withPropertyValues(
                        "kairo.observability.event-otel.enabled=true",
                        "kairo.observability.event-otel.include-domains=security,execution",
                        "kairo.observability.event-otel.redact-attribute-patterns[0]=.*password.*")
                .withUserConfiguration(TestInfra.class)
                .run(
                        ctx -> {
                            KairoEventBus bus = ctx.getBean(KairoEventBus.class);
                            InMemoryLogRecordExporter logs =
                                    ctx.getBean(InMemoryLogRecordExporter.class);

                            bus.publish(
                                    KairoEvent.of(
                                            KairoEvent.DOMAIN_EXECUTION,
                                            "MODEL_TURN",
                                            Map.of("password", "hunter2", "user", "alice")));

                            List<LogRecordData> records = logs.getFinishedLogRecordItems();
                            assertThat(records).hasSize(1);
                            assertThat(
                                            records.get(0)
                                                    .getAttributes()
                                                    .get(
                                                            io.opentelemetry.api.common.AttributeKey
                                                                    .stringKey(
                                                                            "kairo.execution.password")))
                                    .isEqualTo("<redacted>");
                            assertThat(
                                            records.get(0)
                                                    .getAttributes()
                                                    .get(
                                                            io.opentelemetry.api.common.AttributeKey
                                                                    .stringKey(
                                                                            "kairo.execution.user")))
                                    .isEqualTo("alice");
                        });
    }

    @Test
    void autoStartFalse_exporterWiredButNotSubscribed() {
        runner.withPropertyValues(
                        "kairo.observability.event-otel.enabled=true",
                        "kairo.observability.event-otel.auto-start=false")
                .withUserConfiguration(TestInfra.class)
                .run(
                        ctx -> {
                            KairoEventBus bus = ctx.getBean(KairoEventBus.class);
                            KairoEventOTelExporter exporter =
                                    ctx.getBean(KairoEventOTelExporter.class);
                            InMemoryLogRecordExporter logs =
                                    ctx.getBean(InMemoryLogRecordExporter.class);

                            bus.publish(
                                    KairoEvent.of(
                                            KairoEvent.DOMAIN_SECURITY,
                                            "GUARDRAIL_DENY",
                                            Map.of()));

                            assertThat(exporter.exportedCount()).isZero();
                            assertThat(logs.getFinishedLogRecordItems()).isEmpty();
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
    static class TestInfra {
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
    }
}
