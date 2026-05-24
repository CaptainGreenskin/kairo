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
package io.kairo.spring;

import io.kairo.core.health.AgentCallObserver;
import io.kairo.observability.AgentCallMetrics;
import io.kairo.observability.AgentMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = AgentActuatorAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
public class AgentMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AgentMetrics agentMetrics(MeterRegistry registry) {
        return new AgentMetrics(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public AgentCallMetrics agentCallMetrics(MeterRegistry registry) {
        AgentCallMetrics metrics = new AgentCallMetrics(registry);
        AgentCallObserver.setGlobal(metrics);
        return metrics;
    }
}
