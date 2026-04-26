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

import io.kairo.api.event.KairoEventBus;
import io.kairo.observability.event.KairoEventOTelExporter;
import io.opentelemetry.api.logs.LoggerProvider;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the Kairo observability starter. Wires a {@link KairoEventOTelExporter}
 * bridging {@link KairoEventBus} to an OpenTelemetry {@link LoggerProvider} when both are available
 * and the user opts in via {@code kairo.observability.event-otel.enabled=true}.
 *
 * <p><b>Deny-safe:</b> no LoggerProvider bean → no exporter. Applications must bring their own OTel
 * SDK wiring (typically the OTel Spring Boot starter or manual configuration) so this module never
 * fabricates a transport the operator did not ask for.
 *
 * @since v0.9
 */
@AutoConfiguration
@ConditionalOnClass({KairoEventOTelExporter.class, LoggerProvider.class})
@ConditionalOnProperty(
        prefix = "kairo.observability.event-otel",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityAutoConfiguration.class);

    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean
    @ConditionalOnBean({KairoEventBus.class, LoggerProvider.class})
    public KairoEventOTelExporter kairoEventOTelExporter(
            KairoEventBus bus, LoggerProvider loggerProvider, ObservabilityProperties props) {
        ObservabilityProperties.EventOTel cfg = props.getEventOtel();
        Pattern redactor = compile(cfg);
        KairoEventOTelExporter exporter =
                new KairoEventOTelExporter(
                        bus,
                        loggerProvider,
                        cfg.getIncludeDomains(),
                        cfg.getSamplingRatio(),
                        redactor);
        if (cfg.isAutoStart()) {
            exporter.start();
            log.info(
                    "Kairo event→OTel exporter started (domains={}, samplingRatio={})",
                    cfg.getIncludeDomains(),
                    cfg.getSamplingRatio());
        } else {
            log.info(
                    "Kairo event→OTel exporter wired but not started (kairo.observability.event-otel.auto-start=false)");
        }
        return exporter;
    }

    private static Pattern compile(ObservabilityProperties.EventOTel cfg) {
        if (cfg.getRedactAttributePatterns() == null
                || cfg.getRedactAttributePatterns().isEmpty()) {
            return null;
        }
        String combined =
                cfg.getRedactAttributePatterns().stream()
                        .map(p -> "(?:" + p + ")")
                        .collect(Collectors.joining("|"));
        return Pattern.compile(combined);
    }
}
