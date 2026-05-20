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

import io.kairo.api.agent.SubagentRegistry;
import io.kairo.api.mcp.McpPlugin;
import io.kairo.api.plugin.PluginManager;
import io.kairo.api.skill.SkillRegistry;
import io.kairo.plugin.ComponentRegistrar;
import io.kairo.plugin.DefaultPluginManager;
import io.kairo.plugin.DefaultPluginRegistry;
import io.kairo.plugin.DefaultSubagentRegistry;
import io.kairo.plugin.KairoComponentRegistrar;
import io.kairo.plugin.PluginEnvironment;
import io.kairo.plugin.PluginLoader;
import io.kairo.plugin.installer.PluginCacheManager;
import io.kairo.plugin.mcp.PluginMcpRegistrar;
import io.kairo.plugin.source.GitHubSourceFetcher;
import io.kairo.plugin.source.GitSubdirSourceFetcher;
import io.kairo.plugin.source.GitUrlSourceFetcher;
import io.kairo.plugin.source.HttpDownloader;
import io.kairo.plugin.source.LocalPathSourceFetcher;
import io.kairo.plugin.source.NpmSourceFetcher;
import io.kairo.plugin.source.SourceFetcherRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the Kairo Plugin SPI.
 *
 * <p>Activates when {@code kairo-plugin} is on the classpath and {@code kairo.plugin.enabled} is
 * true (default). Wires:
 *
 * <ul>
 *   <li>{@link PluginEnvironment} — per-plugin PATH aggregator
 *   <li>{@link PluginMcpRegistrar} — only if a {@link McpPlugin} bean is present (i.e. the MCP
 *       starter is also on the classpath)
 *   <li>{@link DefaultSubagentRegistry} — default impl; users can override
 *   <li>{@link KairoComponentRegistrar} — bridges plugin components onto the host's registries
 *   <li>{@link SourceFetcherRegistry} — local always, remote 4 conditionally on {@code
 *       kairo.plugin.enable-remote-fetchers=true}
 *   <li>{@link PluginManager} — the canonical entry point
 * </ul>
 *
 * <p>Users that want to customise any piece supply their own {@code @Bean} of the matching type and
 * the matching {@code @ConditionalOnMissingBean} steps aside.
 */
@AutoConfiguration
@ConditionalOnClass(DefaultPluginManager.class)
@ConditionalOnProperty(prefix = "kairo.plugin", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(KairoPluginProperties.class)
public class PluginAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PluginAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public PluginEnvironment kairoPluginEnvironment() {
        return new PluginEnvironment();
    }

    @Bean
    @ConditionalOnMissingBean
    public SubagentRegistry kairoSubagentRegistry() {
        return new DefaultSubagentRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public PluginMcpRegistrar pluginMcpRegistrar(ObjectProvider<McpPlugin> mcpPluginProvider) {
        McpPlugin mcpPlugin = mcpPluginProvider.getIfAvailable();
        if (mcpPlugin == null) {
            log.debug(
                    "No McpPlugin bean on classpath; plugin-declared mcpServers will be captured but"
                            + " not started until kairo-spring-boot-starter-mcp is added");
            return null;
        }
        return new PluginMcpRegistrar(mcpPlugin);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentRegistrar kairoPluginComponentRegistrar(
            ObjectProvider<SkillRegistry> skillRegistryProvider,
            ObjectProvider<PluginMcpRegistrar> mcpRegistrarProvider,
            PluginEnvironment environment,
            SubagentRegistry subagentRegistry) {
        return new KairoComponentRegistrar(
                skillRegistryProvider.getIfAvailable(),
                mcpRegistrarProvider.getIfAvailable(),
                environment,
                subagentRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public SourceFetcherRegistry kairoPluginSourceFetcherRegistry(
            KairoPluginProperties properties) {
        SourceFetcherRegistry registry = new SourceFetcherRegistry();
        registry.register(new LocalPathSourceFetcher());
        if (!properties.isEnableRemoteFetchers()) {
            log.info(
                    "kairo.plugin.enable-remote-fetchers=false — only LocalPath fetcher registered");
            return registry;
        }
        Path cacheRoot = properties.resolvedDataRoot().resolve("cache");
        try {
            Files.createDirectories(cacheRoot);
        } catch (IOException e) {
            log.warn("Failed to create plugin cache dir at {}: {}", cacheRoot, e.getMessage());
        }
        PluginCacheManager cache = new PluginCacheManager(cacheRoot);
        HttpDownloader http = HttpDownloader.jdk();
        registry.register(new GitHubSourceFetcher(cache, http));
        registry.register(new GitUrlSourceFetcher(cache));
        registry.register(new GitSubdirSourceFetcher(cache));
        registry.register(new NpmSourceFetcher(cache, http));
        log.info("Registered all 5 plugin source fetchers (cache root: {})", cacheRoot);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public PluginManager kairoPluginManager(
            KairoPluginProperties properties,
            ComponentRegistrar registrar,
            SourceFetcherRegistry fetchers) {
        Path dataRoot = properties.resolvedDataRoot().resolve("data");
        try {
            Files.createDirectories(dataRoot);
        } catch (IOException e) {
            log.warn("Failed to create plugin data dir at {}: {}", dataRoot, e.getMessage());
        }
        log.info(
                "PluginManager initialised at {} (remote fetchers: {})",
                dataRoot,
                properties.isEnableRemoteFetchers());
        return new DefaultPluginManager(
                new DefaultPluginRegistry(), new PluginLoader(), dataRoot, registrar, fetchers);
    }
}
