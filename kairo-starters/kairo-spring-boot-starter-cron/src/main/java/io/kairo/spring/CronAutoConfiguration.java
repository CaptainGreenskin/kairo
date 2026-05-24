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

import io.kairo.api.cron.CronFireCallback;
import io.kairo.api.cron.CronScheduler;
import io.kairo.cron.CronChainContext;
import io.kairo.cron.CronDeliveryRegistry;
import io.kairo.cron.CronTaskStore;
import io.kairo.cron.DefaultCronScheduler;
import io.kairo.cron.FileCronDelivery;
import io.kairo.cron.LogCronDelivery;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures the Kairo cron scheduler.
 *
 * <p>Activates when {@code kairo-cron} is on the classpath and {@code kairo.cron.enabled} is true
 * (the default). Beans created (each only if no user-defined one exists):
 *
 * <ul>
 *   <li>{@link CronTaskStore} at {@code kairo.cron.store-path} (default {@code
 *       ~/.kairo/cron/tasks.json})
 *   <li>{@link CronChainContext} — task-chain output cache for {@code context_from}
 *   <li>{@link CronDeliveryRegistry} with {@code log} + {@code file} schemes pre-registered
 *   <li>{@link CronFireCallback} — default fallback that just logs at INFO; users override to plug
 *       in agent dispatch
 *   <li>{@link CronScheduler} — {@link DefaultCronScheduler} with all P0 reliability features
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(DefaultCronScheduler.class)
@ConditionalOnProperty(prefix = "kairo.cron", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(KairoCronProperties.class)
public class CronAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CronAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public CronTaskStore kairoCronTaskStore(KairoCronProperties properties) {
        Path storePath = properties.resolvedStorePath();
        if (storePath.getParent() != null) {
            try {
                Files.createDirectories(storePath.getParent());
            } catch (IOException e) {
                log.warn(
                        "Failed to create cron store dir at {}: {}",
                        storePath.getParent(),
                        e.getMessage());
            }
        }
        return new CronTaskStore(storePath);
    }

    @Bean
    @ConditionalOnMissingBean
    public CronChainContext kairoCronChainContext() {
        return new CronChainContext();
    }

    @Bean
    @ConditionalOnMissingBean
    public CronDeliveryRegistry kairoCronDeliveryRegistry() {
        return new CronDeliveryRegistry()
                .register(new LogCronDelivery())
                .register(new FileCronDelivery());
    }

    @Bean
    @ConditionalOnMissingBean
    public CronFireCallback kairoCronFireCallback() {
        // No-op fallback: hosts that want real dispatch (agent prompt injection, no-agent shell,
        // chain pre-pending) should override this with their own bean. This default keeps a
        // freshly-bootstrapped Spring app from blowing up at the first tick.
        return task ->
                log.info(
                        "Cron task fired (no callback override): id={} prompt={}",
                        task.id(),
                        task.prompt());
    }

    @Bean(destroyMethod = "stop")
    @ConditionalOnMissingBean
    public CronScheduler kairoCronScheduler(
            CronTaskStore store, CronFireCallback callback, KairoCronProperties properties) {
        ZoneId zone =
                (properties.getZone() == null || properties.getZone().isBlank())
                        ? ZoneId.systemDefault()
                        : ZoneId.of(properties.getZone());
        DefaultCronScheduler scheduler = new DefaultCronScheduler(store, callback, zone);
        scheduler.start();
        log.info(
                "CronScheduler initialised (store={}, zone={})",
                properties.resolvedStorePath(),
                zone);
        return scheduler;
    }
}
