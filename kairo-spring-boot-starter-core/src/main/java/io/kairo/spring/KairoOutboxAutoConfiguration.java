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

import io.kairo.api.event.KairoEventBus;
import io.kairo.eventstream.outbox.InMemoryOutboxStore;
import io.kairo.eventstream.outbox.OutboxPoller;
import io.kairo.eventstream.outbox.TransactionalOutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for D3 transactional outbox.
 *
 * <p>Activated only when a {@link KairoEventBus} bean is present in the context. Registers an
 * {@link InMemoryOutboxStore}, a {@link TransactionalOutboxPublisher}, and an {@link OutboxPoller}
 * that starts and stops with the Spring lifecycle.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.kairo.eventstream.outbox.InMemoryOutboxStore")
@ConditionalOnBean(KairoEventBus.class)
class KairoOutboxAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KairoOutboxAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    InMemoryOutboxStore inMemoryOutboxStore() {
        return new InMemoryOutboxStore();
    }

    @Bean
    @ConditionalOnMissingBean
    TransactionalOutboxPublisher transactionalOutboxPublisher(
            KairoEventBus kairoEventBus, InMemoryOutboxStore store) {
        log.info("Registered TransactionalOutboxPublisher");
        return new TransactionalOutboxPublisher(kairoEventBus, store);
    }

    @Bean
    @ConditionalOnMissingBean(OutboxPoller.class)
    OutboxPoller outboxPoller(
            InMemoryOutboxStore store,
            KairoEventBus kairoEventBus,
            @Value("${kairo.outbox.max-retries:3}") int maxRetries,
            @Value("${kairo.outbox.batch-size:50}") int batchSize) {
        return new OutboxPoller(store, kairoEventBus, maxRetries, batchSize);
    }

    /** Wrap the {@link OutboxPoller} in a {@link SmartLifecycle} to start/stop with Spring. */
    @Bean
    SmartLifecycle outboxPollerLifecycle(OutboxPoller poller) {
        return new SmartLifecycle() {
            private volatile boolean running;

            @Override
            public void start() {
                poller.start();
                running = true;
                log.info("OutboxPoller lifecycle started");
            }

            @Override
            public void stop() {
                poller.stop();
                running = false;
                log.info("OutboxPoller lifecycle stopped");
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public int getPhase() {
                return Integer.MAX_VALUE - 10;
            }
        };
    }
}
