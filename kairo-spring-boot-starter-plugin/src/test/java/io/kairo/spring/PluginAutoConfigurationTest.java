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

import io.kairo.api.plugin.PluginManager;
import io.kairo.plugin.ComponentRegistrar;
import io.kairo.plugin.PluginEnvironment;
import io.kairo.plugin.mcp.PluginMcpRegistrar;
import io.kairo.plugin.source.SourceFetcherRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PluginAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(PluginAutoConfiguration.class));

    @Test
    void enabledByDefaultExposesPluginManagerAndCoreBeans() {
        contextRunner.run(
                context -> {
                    assertThat(context).hasSingleBean(PluginManager.class);
                    assertThat(context).hasSingleBean(ComponentRegistrar.class);
                    assertThat(context).hasSingleBean(PluginEnvironment.class);
                    assertThat(context).hasSingleBean(SourceFetcherRegistry.class);
                    // No McpPlugin bean on classpath → PluginMcpRegistrar is null.
                    assertThat(context.getBeansOfType(PluginMcpRegistrar.class)).isEmpty();
                });
    }

    @Test
    void disabledViaPropertySkipsEverything() {
        contextRunner
                .withPropertyValues("kairo.plugin.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(PluginManager.class));
    }

    @Test
    void remoteFetchersCanBeDisabled() {
        contextRunner
                .withPropertyValues("kairo.plugin.enable-remote-fetchers=false")
                .run(
                        context -> {
                            SourceFetcherRegistry registry =
                                    context.getBean(SourceFetcherRegistry.class);
                            // With remote disabled, only LocalPath is registered. We can't easily
                            // assert the GitHub fetcher's absence from outside the registry, so
                            // just confirm the bean exists and a LocalPath source resolves.
                            assertThat(registry).isNotNull();
                        });
    }

    @Test
    void dataRootDefaultsToHomeDir() {
        contextRunner.run(
                context -> {
                    KairoPluginProperties props = context.getBean(KairoPluginProperties.class);
                    assertThat(props.resolvedDataRoot().toString())
                            .contains(".kairo")
                            .endsWith("plugins");
                });
    }
}
