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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.event.KairoEvent;
import io.kairo.core.event.DefaultKairoEventBus;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KairoEventOTelExporterTest {

    private DefaultKairoEventBus bus;
    private InMemoryLogRecordExporter logExporter;
    private SdkLoggerProvider loggerProvider;

    @BeforeEach
    void setUp() {
        bus = new DefaultKairoEventBus();
        logExporter = InMemoryLogRecordExporter.create();
        loggerProvider =
                SdkLoggerProvider.builder()
                        .addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter))
                        .build();
    }

    @AfterEach
    void tearDown() {
        loggerProvider.close();
    }

    @Test
    void exportsOneLogRecordPerDomain_withMappedAttributes() {
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
                        Instant.ofEpochMilli(1_000),
                        KairoEvent.DOMAIN_SECURITY,
                        "GUARDRAIL_DENY",
                        null,
                        Map.of("decision", "deny", "rule", "no-secrets")));
        bus.publish(
                new KairoEvent(
                        "exec-1",
                        Instant.ofEpochMilli(2_000),
                        KairoEvent.DOMAIN_EXECUTION,
                        "MODEL_TURN",
                        null,
                        Map.of("model", "gpt-4")));
        bus.publish(
                new KairoEvent(
                        "team-1",
                        Instant.ofEpochMilli(3_000),
                        KairoEvent.DOMAIN_TEAM,
                        "EXPERT_TEAM_ROUND",
                        null,
                        Map.of("round", 1)));

        List<LogRecordData> records = logExporter.getFinishedLogRecordItems();
        assertThat(records).hasSize(3);
        assertThat(exporter.exportedCount()).isEqualTo(3L);
        assertThat(exporter.droppedByDomainCount()).isZero();
        assertThat(exporter.droppedBySamplingCount()).isZero();

        LogRecordData sec = findByEventId(records, "sec-1");
        assertThat(sec.getSeverity()).isEqualTo(Severity.WARN);
        assertThat(sec.getSeverityText()).isEqualTo(KairoEvent.DOMAIN_SECURITY);
        assertThat(sec.getBody().asString()).isEqualTo("GUARDRAIL_DENY");
        assertThat(attr(sec, "kairo.security.decision")).isEqualTo("deny");
        assertThat(attr(sec, "kairo.security.rule")).isEqualTo("no-secrets");
        assertThat(attr(sec, "kairo.domain")).isEqualTo("security");
        assertThat(attr(sec, "kairo.event.type")).isEqualTo("GUARDRAIL_DENY");

        LogRecordData exec = findByEventId(records, "exec-1");
        assertThat(exec.getSeverity()).isEqualTo(Severity.INFO);
        assertThat(attr(exec, "kairo.execution.model")).isEqualTo("gpt-4");

        LogRecordData team = findByEventId(records, "team-1");
        assertThat(attr(team, "kairo.team.round")).isEqualTo("1");

        exporter.stop();
    }

    @Test
    void domainFilter_dropsEventsOutsideIncludeSet() {
        KairoEventOTelExporter exporter =
                new KairoEventOTelExporter(
                        bus, loggerProvider, Set.of(KairoEvent.DOMAIN_SECURITY), 1.0, null);
        exporter.start();

        bus.publish(KairoEvent.of(KairoEvent.DOMAIN_EXECUTION, "MODEL_TURN", Map.of()));
        bus.publish(KairoEvent.of(KairoEvent.DOMAIN_TEAM, "EXPERT_ROUND", Map.of()));
        bus.publish(KairoEvent.of(KairoEvent.DOMAIN_SECURITY, "GUARDRAIL_DENY", Map.of()));

        assertThat(logExporter.getFinishedLogRecordItems()).hasSize(1);
        assertThat(exporter.exportedCount()).isEqualTo(1L);
        assertThat(exporter.droppedByDomainCount()).isEqualTo(2L);

        exporter.stop();
    }

    @Test
    void samplingRatioZero_dropsAllNonSecurityEvents() {
        KairoEventOTelExporter exporter =
                new KairoEventOTelExporter(
                        bus,
                        loggerProvider,
                        Set.of(KairoEvent.DOMAIN_SECURITY, KairoEvent.DOMAIN_EXECUTION),
                        0.0,
                        null);
        exporter.start();

        for (int i = 0; i < 20; i++) {
            bus.publish(KairoEvent.of(KairoEvent.DOMAIN_EXECUTION, "MODEL_TURN", Map.of()));
        }
        bus.publish(KairoEvent.of(KairoEvent.DOMAIN_SECURITY, "GUARDRAIL_DENY", Map.of()));

        assertThat(logExporter.getFinishedLogRecordItems()).hasSize(1);
        assertThat(exporter.exportedCount()).isEqualTo(1L);
        assertThat(exporter.droppedBySamplingCount()).isEqualTo(20L);

        exporter.stop();
    }

    @Test
    void samplingRatioOne_exportsAll() {
        KairoEventOTelExporter exporter =
                new KairoEventOTelExporter(
                        bus,
                        loggerProvider,
                        Set.of(KairoEvent.DOMAIN_SECURITY, KairoEvent.DOMAIN_EXECUTION),
                        1.0,
                        null);
        exporter.start();

        for (int i = 0; i < 20; i++) {
            bus.publish(KairoEvent.of(KairoEvent.DOMAIN_EXECUTION, "MODEL_TURN", Map.of()));
        }

        assertThat(logExporter.getFinishedLogRecordItems()).hasSize(20);
        assertThat(exporter.exportedCount()).isEqualTo(20L);
        assertThat(exporter.droppedBySamplingCount()).isZero();

        exporter.stop();
    }

    @Test
    void securityAlwaysOn_evenWhenSamplingRatioZero() {
        KairoEventOTelExporter exporter =
                new KairoEventOTelExporter(
                        bus, loggerProvider, Set.of(KairoEvent.DOMAIN_SECURITY), 0.0, null);
        exporter.start();

        for (int i = 0; i < 10; i++) {
            bus.publish(KairoEvent.of(KairoEvent.DOMAIN_SECURITY, "GUARDRAIL_DENY", Map.of()));
        }

        assertThat(logExporter.getFinishedLogRecordItems()).hasSize(10);
        assertThat(exporter.exportedCount()).isEqualTo(10L);
        assertThat(exporter.droppedBySamplingCount()).isZero();

        exporter.stop();
    }

    @Test
    void attributeRedaction_masksKeysMatchingPattern() {
        Pattern redactor = Pattern.compile(".*(password|token|secret).*");
        KairoEventOTelExporter exporter =
                new KairoEventOTelExporter(
                        bus, loggerProvider, Set.of(KairoEvent.DOMAIN_SECURITY), 1.0, redactor);
        exporter.start();

        bus.publish(
                new KairoEvent(
                        "sec-redact",
                        Instant.now(),
                        KairoEvent.DOMAIN_SECURITY,
                        "GUARDRAIL_DENY",
                        null,
                        Map.of(
                                "user", "alice",
                                "password", "hunter2",
                                "api_token", "abc123")));

        List<LogRecordData> records = logExporter.getFinishedLogRecordItems();
        assertThat(records).hasSize(1);
        LogRecordData record = records.get(0);
        assertThat(attr(record, "kairo.security.user")).isEqualTo("alice");
        assertThat(attr(record, "kairo.security.password")).isEqualTo("<redacted>");
        assertThat(attr(record, "kairo.security.api_token")).isEqualTo("<redacted>");

        exporter.stop();
    }

    @Test
    void startIsIdempotent_andStopReleasesSubscription() {
        KairoEventOTelExporter exporter =
                new KairoEventOTelExporter(
                        bus, loggerProvider, Set.of(KairoEvent.DOMAIN_SECURITY), 1.0, null);
        exporter.start();
        exporter.start(); // should be a no-op, not a double subscription

        bus.publish(KairoEvent.of(KairoEvent.DOMAIN_SECURITY, "GUARDRAIL_DENY", Map.of()));
        assertThat(logExporter.getFinishedLogRecordItems()).hasSize(1);

        exporter.stop();

        bus.publish(KairoEvent.of(KairoEvent.DOMAIN_SECURITY, "GUARDRAIL_DENY", Map.of()));
        // still 1 because we stopped
        assertThat(logExporter.getFinishedLogRecordItems()).hasSize(1);
    }

    @Test
    void stopIsIdempotent() {
        KairoEventOTelExporter exporter =
                new KairoEventOTelExporter(
                        bus, loggerProvider, Set.of(KairoEvent.DOMAIN_SECURITY), 1.0, null);
        exporter.start();
        exporter.stop();
        exporter.stop(); // must not throw
    }

    @Test
    void rejectsInvalidSamplingRatio() {
        assertThatIllegalSamplingRatio(-0.1);
        assertThatIllegalSamplingRatio(1.5);
    }

    private void assertThatIllegalSamplingRatio(double ratio) {
        try {
            new KairoEventOTelExporter(
                    bus, loggerProvider, Set.of(KairoEvent.DOMAIN_SECURITY), ratio, null);
            throw new AssertionError("expected IllegalArgumentException for ratio=" + ratio);
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    private static LogRecordData findByEventId(List<LogRecordData> records, String eventId) {
        return records.stream()
                .filter(r -> eventId.equals(attr(r, "kairo.event.id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no log record for event.id=" + eventId));
    }

    private static String attr(LogRecordData record, String key) {
        Object value =
                record.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey(key));
        return value == null ? null : value.toString();
    }
}
