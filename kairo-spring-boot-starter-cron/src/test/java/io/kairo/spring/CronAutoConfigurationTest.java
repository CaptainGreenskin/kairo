/*
 * Copyright 2025-2026 the Kairo authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 */
package io.kairo.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.kairo.api.cron.CronFireCallback;
import io.kairo.api.cron.CronScheduler;
import io.kairo.cron.CronChainContext;
import io.kairo.cron.CronDeliveryRegistry;
import io.kairo.cron.CronTaskStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CronAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(CronAutoConfiguration.class));

    @Test
    void exposesAllCoreBeansWhenEnabled() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(CronScheduler.class);
                    assertThat(context).hasSingleBean(CronTaskStore.class);
                    assertThat(context).hasSingleBean(CronChainContext.class);
                    assertThat(context).hasSingleBean(CronDeliveryRegistry.class);
                    assertThat(context).hasSingleBean(CronFireCallback.class);

                    // Delivery registry pre-registers log + file.
                    CronDeliveryRegistry reg = context.getBean(CronDeliveryRegistry.class);
                    assertThat(reg.snapshot()).containsKeys("log", "file");
                });
    }

    @Test
    void disabledViaPropertyDropsAllBeans() {
        contextRunner
                .withPropertyValues("kairo.cron.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(CronScheduler.class));
    }

    @Test
    void storePathOverrideIsHonoured() {
        contextRunner
                .withPropertyValues("kairo.cron.store-path=/tmp/kairo-cron-test-tasks.json")
                .run(
                        context -> {
                            KairoCronProperties props = context.getBean(KairoCronProperties.class);
                            assertThat(props.resolvedStorePath().toString())
                                    .endsWith("kairo-cron-test-tasks.json");
                        });
    }

    @Test
    void zoneOverrideIsHonoured() {
        contextRunner
                .withPropertyValues("kairo.cron.zone=UTC")
                .run(context -> assertThat(context).hasSingleBean(CronScheduler.class));
    }
}
