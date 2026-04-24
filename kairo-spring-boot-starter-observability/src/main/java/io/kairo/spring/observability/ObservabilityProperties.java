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

import io.kairo.api.event.KairoEvent;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Kairo observability starter.
 *
 * <p>All properties are under {@code kairo.observability.*}. The starter is opt-in: nothing wires
 * unless {@code kairo.observability.event-otel.enabled=true}.
 *
 * @since v0.9
 */
@ConfigurationProperties(prefix = "kairo.observability")
public class ObservabilityProperties {

    private final EventOTel eventOtel = new EventOTel();

    public EventOTel getEventOtel() {
        return eventOtel;
    }

    /** Event-bus to OpenTelemetry bridge configuration. */
    public static class EventOTel {

        /** Master switch; default off matching every other v0.9 starter. */
        private boolean enabled = false;

        /**
         * Start the exporter immediately when the bean is created. Default on so simple setups just
         * work; advanced deployments can flip this off and drive {@code start()} / {@code stop()}
         * from their own lifecycle.
         */
        private boolean autoStart = true;

        /**
         * Domains whose envelopes are forwarded as OTel log records. Defaults to security only —
         * the "always on" observability the scoping doc calls out. Apps that want execution/team
         * tracing MUST opt in explicitly.
         */
        private Set<String> includeDomains = Set.of(KairoEvent.DOMAIN_SECURITY);

        /**
         * Sampling ratio in {@code [0.0, 1.0]} applied to non-security envelopes. Security is
         * always-on regardless. Defaults to 1.0 so opt-in domains are fully observed; operators can
         * throttle under load.
         */
        private double samplingRatio = 1.0;

        /**
         * Regex patterns matched against the flat attribute key (e.g. {@code
         * kairo.execution.prompt}). If any pattern matches, the value is replaced with {@code
         * <redacted>} before being attached to the log record. Keys are joined into a single
         * alternation regex to keep the hot path allocation-free.
         */
        private List<String> redactAttributePatterns = List.of();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAutoStart() {
            return autoStart;
        }

        public void setAutoStart(boolean autoStart) {
            this.autoStart = autoStart;
        }

        public Set<String> getIncludeDomains() {
            return includeDomains;
        }

        public void setIncludeDomains(Set<String> includeDomains) {
            this.includeDomains = includeDomains;
        }

        public double getSamplingRatio() {
            return samplingRatio;
        }

        public void setSamplingRatio(double samplingRatio) {
            this.samplingRatio = samplingRatio;
        }

        public List<String> getRedactAttributePatterns() {
            return redactAttributePatterns;
        }

        public void setRedactAttributePatterns(List<String> redactAttributePatterns) {
            this.redactAttributePatterns = redactAttributePatterns;
        }
    }
}
