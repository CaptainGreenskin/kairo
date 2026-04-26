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

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.agent.SnapshotStore;
import io.kairo.api.memory.EmbeddingProvider;
import io.kairo.api.memory.MemoryStore;
import io.kairo.api.model.ModelConfig;
import io.kairo.api.model.ModelProvider;
import io.kairo.api.model.ModelResponse;
import io.kairo.core.agent.snapshot.CheckpointManager;
import io.kairo.core.agent.snapshot.InMemorySnapshotStore;
import io.kairo.core.memory.InMemoryStore;
import io.kairo.core.memory.JdbcMemoryStore;
import io.kairo.core.memory.NoopEmbeddingProvider;
import java.util.List;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Tests for v0.5 SPI auto-configuration: EmbeddingProvider, MemoryStore (jdbc), and
 * CheckpointManager.
 */
class MemoryAndEmbeddingAutoConfigurationTest {

    private static final ModelProvider NOOP_PROVIDER =
            new ModelProvider() {
                @Override
                public Mono<ModelResponse> call(
                        List<io.kairo.api.message.Msg> messages, ModelConfig config) {
                    return Mono.empty();
                }

                @Override
                public Flux<ModelResponse> stream(
                        List<io.kairo.api.message.Msg> messages, ModelConfig config) {
                    return Flux.empty();
                }

                @Override
                public String name() {
                    return "noop";
                }
            };

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    AgentRuntimeAutoConfiguration.class,
                                    SnapshotAutoConfiguration.class))
                    .withBean("modelProvider", ModelProvider.class, () -> NOOP_PROVIDER);

    // ---- EmbeddingProvider Tests ----

    @Test
    void defaultEmbeddingProviderIsNoop() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(EmbeddingProvider.class);
                    assertThat(context.getBean(EmbeddingProvider.class))
                            .isInstanceOf(NoopEmbeddingProvider.class);
                });
    }

    @Test
    void customEmbeddingProviderOverridesDefault() {
        EmbeddingProvider custom =
                new EmbeddingProvider() {
                    @Override
                    public Mono<float[]> embed(String text) {
                        return Mono.just(new float[] {1.0f, 2.0f});
                    }

                    @Override
                    public int dimensions() {
                        return 2;
                    }
                };

        runner.withBean("customEmbeddingProvider", EmbeddingProvider.class, () -> custom)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(EmbeddingProvider.class);
                            assertThat(context.getBean(EmbeddingProvider.class)).isSameAs(custom);
                        });
    }

    // ---- MemoryStore Tests ----

    @Test
    void defaultMemoryStoreIsInMemory() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(MemoryStore.class);
                    assertThat(context.getBean(MemoryStore.class))
                            .isInstanceOf(InMemoryStore.class);
                });
    }

    @Test
    void jdbcMemoryStoreWhenConfigured() {
        runner.withPropertyValues("kairo.memory.store-type=jdbc")
                .withBean(
                        "dataSource",
                        DataSource.class,
                        () -> {
                            JdbcDataSource ds = new JdbcDataSource();
                            ds.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
                            return ds;
                        })
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(MemoryStore.class);
                            assertThat(context.getBean(MemoryStore.class))
                                    .isInstanceOf(JdbcMemoryStore.class);
                        });
    }

    @Test
    void jdbcMemoryStoreFailsWithoutDataSource() {
        runner.withPropertyValues("kairo.memory.store-type=jdbc")
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .rootCause()
                                    .isInstanceOf(IllegalStateException.class)
                                    .hasMessageContaining("DataSource required");
                        });
    }

    @Test
    void customMemoryStoreOverridesDefault() {
        MemoryStore custom = new InMemoryStore();
        runner.withBean("customMemoryStore", MemoryStore.class, () -> custom)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(MemoryStore.class);
                            assertThat(context.getBean(MemoryStore.class)).isSameAs(custom);
                        });
    }

    // ---- CheckpointManager Tests ----

    @Test
    void checkpointManagerCreatedWhenEnabled() {
        runner.withPropertyValues("kairo.checkpoint.enabled=true")
                .withBean("snapshotStore", SnapshotStore.class, InMemorySnapshotStore::new)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(CheckpointManager.class);
                        });
    }

    @Test
    void checkpointManagerNotCreatedWhenDisabled() {
        runner.withPropertyValues("kairo.checkpoint.enabled=false")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(CheckpointManager.class);
                        });
    }

    @Test
    void checkpointManagerNotCreatedByDefault() {
        runner.run(
                context -> {
                    assertThat(context).doesNotHaveBean(CheckpointManager.class);
                });
    }
}
