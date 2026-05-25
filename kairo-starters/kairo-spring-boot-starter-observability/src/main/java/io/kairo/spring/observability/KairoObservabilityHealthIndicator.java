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

import io.kairo.observability.event.KairoEventOTelExporter;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Spring Actuator health contribution for the Kairo → OpenTelemetry event bridge.
 *
 * <p>Reports {@code UP} while {@link KairoEventOTelExporter#exportFailedCount()} stays zero and
 * {@code DOWN} the moment any envelope fails to emit — exposing the failure counter, the
 * exported-success counter, and the last exception string so {@code /actuator/health} surfaces a
 * silent OTel SDK misconfiguration instead of hiding it behind {@code LOG.warn}.
 *
 * <p>Registered only when both Spring Actuator and a {@link KairoEventOTelExporter} bean are
 * present — apps that do not opt into the OTel bridge are unaffected.
 *
 * @since v0.9
 */
public class KairoObservabilityHealthIndicator implements HealthIndicator {

    private final KairoEventOTelExporter exporter;

    public KairoObservabilityHealthIndicator(KairoEventOTelExporter exporter) {
        this.exporter = exporter;
    }

    @Override
    public Health health() {
        long failed = exporter.exportFailedCount();
        long exported = exporter.exportedCount();
        Health.Builder builder = failed == 0 ? Health.up() : Health.down();
        builder.withDetail("exportedCount", exported)
                .withDetail("exportFailedCount", failed)
                .withDetail("droppedByDomainCount", exporter.droppedByDomainCount())
                .withDetail("droppedBySamplingCount", exporter.droppedBySamplingCount());
        String lastError = exporter.lastExportError();
        if (lastError != null) {
            builder.withDetail("lastExportError", lastError);
        }
        return builder.build();
    }
}
