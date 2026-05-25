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
import io.opentelemetry.api.trace.Tracer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * Spring Actuator endpoint exposing Kairo runtime metrics at {@code /actuator/kairo-metrics}.
 *
 * <p>Returns tracer metadata plus, when present, {@link KairoEventOTelExporter} counters so
 * operators can sanity-check whether events are flowing through the OTel bridge without scraping
 * the SDK's own metric set.
 */
@Endpoint(id = "kairo-metrics")
public class KairoMetricsEndpoint {

    private static final String VERSION = "1.0.0-SNAPSHOT";

    private final Tracer tracer;
    private final KairoEventOTelExporter eventExporter;

    public KairoMetricsEndpoint(Tracer tracer) {
        this(tracer, null);
    }

    public KairoMetricsEndpoint(Tracer tracer, KairoEventOTelExporter eventExporter) {
        this.tracer = tracer;
        this.eventExporter = eventExporter;
    }

    @ReadOperation
    public Map<String, Object> metrics() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tracerName", tracer != null ? tracer.getClass().getSimpleName() : "none");
        result.put("isEnabled", tracer != null);
        result.put("version", VERSION);

        Map<String, Object> exporter = new LinkedHashMap<>();
        if (eventExporter != null) {
            exporter.put("present", true);
            exporter.put("exportedCount", eventExporter.exportedCount());
            exporter.put("droppedByDomainCount", eventExporter.droppedByDomainCount());
            exporter.put("droppedBySamplingCount", eventExporter.droppedBySamplingCount());
            exporter.put("exportFailedCount", eventExporter.exportFailedCount());
            String lastError = eventExporter.lastExportError();
            if (lastError != null) {
                exporter.put("lastExportError", lastError);
            }
        } else {
            exporter.put("present", false);
        }
        result.put("eventExporter", exporter);
        return result;
    }
}
