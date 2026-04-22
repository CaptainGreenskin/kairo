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

import io.kairo.api.memory.EmbeddingProvider;
import io.kairo.api.memory.MemoryStore;
import io.kairo.core.agent.snapshot.CheckpointManager;
import io.kairo.core.memory.FileMemoryStore;
import io.kairo.core.memory.InMemoryStore;
import io.kairo.core.memory.JdbcMemoryStore;
import io.kairo.core.memory.NoopEmbeddingProvider;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Memory and embedding auto-configuration: memory store, embedding provider, and checkpoint
 * manager.
 *
 * <p>Imported by {@link AgentRuntimeAutoConfiguration}.
 */
@Configuration(proxyBeanMethods = false)
class MemoryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MemoryAutoConfiguration.class);

    // ---- Embedding Provider ----

    @Bean
    @ConditionalOnMissingBean
    EmbeddingProvider embeddingProvider() {
        log.info("Using NoopEmbeddingProvider (no vector search)");
        return new NoopEmbeddingProvider();
    }

    // ---- Memory Store ----

    @Bean
    @ConditionalOnMissingBean
    MemoryStore memoryStore(
            AgentRuntimeProperties properties, ObjectProvider<DataSource> dataSource) {
        String storeType = properties.getMemory().resolveStoreType();
        return switch (storeType) {
            case "jdbc" -> {
                DataSource ds = dataSource.getIfAvailable();
                if (ds == null) {
                    throw new IllegalStateException(
                            "DataSource required for jdbc memory store. "
                                    + "Add a DataSource bean or spring-boot-starter-jdbc dependency.");
                }
                log.info("Using JDBC-based memory store");
                yield new JdbcMemoryStore(ds);
            }
            case "file" -> {
                String path = properties.getMemory().getFileStorePath();
                if (path == null || path.isBlank()) {
                    path = System.getProperty("user.home") + "/.kairo/memory";
                }
                log.info("Using file-based memory store at: {}", path);
                yield new FileMemoryStore(Path.of(path));
            }
            default -> {
                log.info("Using in-memory store");
                yield new InMemoryStore();
            }
        };
    }

    // ---- Checkpoint Manager ----

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            name = "kairo.checkpoint.enabled",
            havingValue = "true",
            matchIfMissing = false)
    CheckpointManager checkpointManager(io.kairo.api.agent.SnapshotStore snapshotStore) {
        log.info("Configured CheckpointManager");
        return new CheckpointManager(snapshotStore);
    }
}
