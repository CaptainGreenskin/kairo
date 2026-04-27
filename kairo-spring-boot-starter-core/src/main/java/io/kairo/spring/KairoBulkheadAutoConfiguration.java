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

import io.kairo.api.tenant.TenantContextHolder;
import io.kairo.core.middleware.BulkheadMiddleware;
import io.kairo.core.tenant.TenantBulkheadConfig;
import io.kairo.core.tenant.TenantBulkheadRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for D1 per-tenant bulkhead isolation.
 *
 * <p>Registers a {@link TenantBulkheadRegistry} and a {@link BulkheadMiddleware} bean. Reads the
 * default tier limits from {@code kairo.bulkhead.*} properties (falls back to built-in defaults
 * when not set).
 *
 * <p>Enable by adding this starter to the classpath; disable with {@code
 * kairo.bulkhead.enabled=false}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "kairo.bulkhead.enabled", havingValue = "true", matchIfMissing = true)
class KairoBulkheadAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KairoBulkheadAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    TenantBulkheadRegistry tenantBulkheadRegistry(
            @Value("${kairo.bulkhead.max-concurrency:10}") int maxConcurrency,
            @Value("${kairo.bulkhead.requests-per-second:100.0}") double requestsPerSecond,
            @Value("${kairo.bulkhead.burst-capacity:200}") long burstCapacity) {
        TenantBulkheadConfig config =
                new TenantBulkheadConfig(maxConcurrency, requestsPerSecond, burstCapacity);
        log.info(
                "Configured TenantBulkheadRegistry (maxConcurrency={}, rps={}, burst={})",
                maxConcurrency,
                requestsPerSecond,
                burstCapacity);
        return new TenantBulkheadRegistry(config);
    }

    @Bean
    @ConditionalOnMissingBean
    BulkheadMiddleware bulkheadMiddleware(
            TenantBulkheadRegistry registry, TenantContextHolder tenantContextHolder) {
        log.info("Registered BulkheadMiddleware");
        return new BulkheadMiddleware(registry, tenantContextHolder);
    }
}
