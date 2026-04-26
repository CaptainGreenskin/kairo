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

import io.kairo.api.agent.SnapshotStore;
import io.kairo.core.agent.snapshot.InMemorySnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Kairo Agent Snapshot support.
 *
 * <p>Only activates when {@link SnapshotStore} is on the classpath. Provides a default in-memory
 * implementation that can be overridden by user-defined beans (e.g., file-based, JDBC, or Redis
 * implementations).
 *
 * <p>Disable entirely with {@code kairo.snapshot.enabled=false}.
 */
@AutoConfiguration
@ConditionalOnClass(SnapshotStore.class)
@ConditionalOnProperty(prefix = "kairo.snapshot", name = "enabled", matchIfMissing = true)
public class SnapshotAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SnapshotAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SnapshotStore snapshotStore() {
        log.info("Configured in-memory SnapshotStore");
        return new InMemorySnapshotStore();
    }
}
