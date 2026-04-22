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

import io.kairo.api.agent.AgentSnapshot;
import io.kairo.api.agent.SnapshotStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Tests for {@link SnapshotAutoConfiguration}. */
class SnapshotAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(SnapshotAutoConfiguration.class));

    @Test
    void defaultBeanIsCreated() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(SnapshotStore.class);
                    assertThat(context.getBean(SnapshotStore.class).getClass().getSimpleName())
                            .isEqualTo("InMemorySnapshotStore");
                });
    }

    @Test
    void userDefinedSnapshotStoreTakesPrecedence() {
        SnapshotStore custom =
                new SnapshotStore() {
                    @Override
                    public Mono<Void> save(String key, AgentSnapshot snapshot) {
                        return Mono.empty();
                    }

                    @Override
                    public Mono<AgentSnapshot> load(String key) {
                        return Mono.empty();
                    }

                    @Override
                    public Mono<Void> delete(String key) {
                        return Mono.empty();
                    }

                    @Override
                    public Flux<String> listKeys(String agentIdPrefix) {
                        return Flux.empty();
                    }
                };
        runner.withBean(SnapshotStore.class, () -> custom)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(SnapshotStore.class);
                            assertThat(context.getBean(SnapshotStore.class)).isSameAs(custom);
                        });
    }

    @Test
    void disabledViaProperty() {
        runner.withPropertyValues("kairo.snapshot.enabled=false")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(SnapshotStore.class);
                        });
    }

    @Test
    void enabledByDefault() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(SnapshotStore.class);
                });
    }
}
